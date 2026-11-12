package com.drivecash.app.data.signals

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.drivecash.app.domain.Gear
import com.drivecash.app.domain.Sample

/**
 * Pleos Connect SDK 4종과 AAOS 표준 속성을 묶어 [Sample]을 만든다.
 *
 * 신호 출처 (docs/DEVELOPMENT.md 4장, 에뮬레이터에서 확인):
 * - 속도: AAOS PERF_VEHICLE_SPEED (V-02, D-017)
 * - 기어: AAOS GEAR_SELECTION 우선, Pleos Gear API(deprecated) 보조 (V-04, D-018)
 * - 주행거리: Vehicle SDK Odometer (V-01)
 * - 브레이크: Vehicle SDK Brake (V-03)
 * - 제한속도: NaviHelper onCurrentRoadSpeedLimit (V-06)
 * - 스팟 구간: FusedLocation 근접 알림 (V-07)
 */
class PleosVehicleSignals(
    private val context: Context,
    private val zones: List<BonusZone>,
) : VehicleSignals {

    private val handler = Handler(Looper.getMainLooper())
    private val aaos = AaosCar(context)

    private var started = false
    private var vehicle: Vehicle? = null
    private var naviHelper: NaviHelper? = null
    private var fused: FusedLocation? = null
    private var zoneListener: ((String) -> Unit)? = null

    private var aaosThread: HandlerThread? = null
    private var aaosHandler: Handler? = null
    @Volatile private var aaosSpeedMps: Float? = null
    @Volatile private var aaosGearRaw: Int? = null

    @Volatile private var odometerKm: Float? = null
    @Volatile private var pleosGear: Gear? = null
    @Volatile private var brakePressed: Boolean? = null
    @Volatile private var speedLimitKmh: Int? = null
    @Volatile private var location: LocationData? = null
    @Volatile private var naviConnected = false
    @Volatile private var fusedActive = false
    @Volatile private var zonesRegistered = 0

    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            odometerKm = value
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "[SIGNAL] Odometer 리스너 실패: $e")
        }
    }

    @Suppress("DEPRECATION")
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            pleosGear = gear.toGear()
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "[SIGNAL] Gear 리스너 실패: $e")
        }
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) {
            brakePressed = status
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "[SIGNAL] Brake 리스너 실패: $e")
        }
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
            // 경로 안내가 없으면 0이 온다. 0은 "정보 없음"으로 처리한다.
            speedLimitKmh = speedLimit.takeIf { it > 0 }
        }
    }

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            this@PleosVehicleSignals.location = location
        }

        override fun onError(exception: Exception) {
            Log.w(TAG, "[SIGNAL] 위치 오류: $exception")
        }
    }

    override fun start() {
        if (started) return
        started = true
        Log.i(TAG, "[SIGNAL] 시작")

        aaos.connect()
        startAaosPolling()
        vehicle = Vehicle(context).also { it.initialize() }
        naviHelper = NaviHelper(context)
        fused = FusedLocation(context)

        // 검증 앱에서 Vehicle.initialize() 후 약 2초 뒤 호출이 성공했다.
        handler.postDelayed({ registerVehicle() }, VEHICLE_REGISTER_DELAY_MS)
        handler.postDelayed({ connectNavi() }, VEHICLE_REGISTER_DELAY_MS)
        handler.postDelayed({ startFused(attempt = 0) }, VEHICLE_REGISTER_DELAY_MS)
    }

    private fun startAaosPolling() {
        val t = HandlerThread("aaos-poll").also { it.start() }
        aaosThread = t
        val h = Handler(t.looper)
        aaosHandler = h
        h.post(object : Runnable {
            override fun run() {
                if (!started) return
                aaosSpeedMps = aaos.speedMps()
                aaosGearRaw = aaos.gearRaw()
                h.postDelayed(this, AAOS_POLL_MS)
            }
        })
    }

    override fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacksAndMessages(null)
        aaosHandler?.removeCallbacksAndMessages(null)
        aaosThread?.quitSafely()
        aaosThread = null
        aaosHandler = null
        runCatching { vehicle?.getOdometer()?.unregisterDistanceDriven(odometerListener) }
        runCatching { @Suppress("DEPRECATION") vehicle?.getGear()?.unregisterGearSelection(gearListener) }
        runCatching { vehicle?.getBrake()?.unregisterBrakePedalStatus(brakeListener) }
        runCatching { naviHelper?.removeListener(naviListener) }
        runCatching { fused?.unregisterFusedLocationCallback(locationListener) }
        runCatching { vehicle?.release() }
        runCatching { naviHelper?.release() }
        runCatching { fused?.release() }
        vehicle = null
        naviHelper = null
        fused = null
    }

    override fun snapshot(nowMs: Long): Sample {
        // AAOS 속성 읽기는 바인더 동기 호출이라 메인 스레드에서 하면 차량 서비스가 바쁠 때 ANR 이 난다.
        // 읽기는 별도 스레드가 1초마다 하고, 여기서는 마지막 값만 돌려준다. (에뮬레이터에서 ANR 확인)
        val gear = aaosGearRaw.toGearFromAaos() ?: pleosGear ?: Gear.UNKNOWN
        return Sample(
            timeMs = nowMs,
            speedMps = aaosSpeedMps,
            gear = gear,
            odometerKm = odometerKm,
            brakePressed = brakePressed,
            speedLimitKmh = speedLimitKmh,
        )
    }

    override fun currentLocation(): Pair<Double, Double>? =
        location?.let { it.latitude to it.longitude }

    override fun setZoneListener(listener: (String) -> Unit) {
        zoneListener = listener
    }

    override fun statusSummary(): String =
        "AAOS=${aaosSpeedMps != null} 내비=$naviConnected 위치=$fusedActive 구간=$zonesRegistered/${zones.size} " +
            "주행거리=${odometerKm ?: "-"} 제한속도=${speedLimitKmh ?: "-"}"

    // ------------------------------------------------------------------ 등록

    private fun registerVehicle() {
        val v = vehicle ?: return
        try {
            v.getOdometer().registerDistanceDriven(odometerListener)
            v.getOdometer().getCurrentDistanceDriven(
                onComplete = { km -> odometerKm = km },
                onFailed = { e -> Log.w(TAG, "[SIGNAL] 주행거리 조회 실패: $e") },
            )
            @Suppress("DEPRECATION")
            v.getGear().registerGearSelection(gearListener)
            v.getBrake().registerBrakePedalStatus(brakeListener)
            Log.i(TAG, "[SIGNAL] Vehicle 리스너 등록")
        } catch (t: Throwable) {
            Log.w(TAG, "[SIGNAL] Vehicle 등록 실패: $t")
        }
    }

    private fun connectNavi() {
        val navi = naviHelper ?: return
        try {
            navi.addListener(naviListener)
            navi.initialize(
                onConnected = {
                    naviConnected = true
                    Log.i(TAG, "[SIGNAL] NaviHelper 연결")
                },
                onFailed = { t -> Log.w(TAG, "[SIGNAL] NaviHelper 연결 실패: $t") },
            )
        } catch (t: Throwable) {
            Log.w(TAG, "[SIGNAL] NaviHelper 시작 실패: $t")
        }
    }

    /** 위치 퍼미션은 런타임 허용이 필요해서, 허용될 때까지 몇 번 다시 시도한다. */
    private fun startFused(attempt: Int) {
        val fl = fused ?: return
        if (!started) return
        val granted = context.checkSelfPermission(PERM_FUSED_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted || !runCatching { fl.isSupported() }.getOrDefault(false)) {
            if (attempt < FUSED_RETRY_MAX) {
                handler.postDelayed({ startFused(attempt + 1) }, FUSED_RETRY_MS)
            } else {
                Log.w(TAG, "[SIGNAL] FusedLocation 사용 불가(퍼미션 또는 서비스 없음). 스팟 구간 보너스 비활성")
            }
            return
        }
        try {
            fl.initialize()
            handler.postDelayed({
                fl.registerFusedLocationCallback(locationListener)
                fusedActive = true
                zones.forEach { registerZone(fl, it) }
                Log.i(TAG, "[SIGNAL] FusedLocation 시작, 구간 ${zones.size}개 등록 요청")
            }, 1_000)
        } catch (t: Throwable) {
            Log.w(TAG, "[SIGNAL] FusedLocation 시작 실패: $t")
        }
    }

    private fun registerZone(fl: FusedLocation, zone: BonusZone) {
        val request = ProximityAlertRequest(
            latitude = zone.latitude,
            longitude = zone.longitude,
            radiusMeters = zone.radiusMeters,
            transitionTypes = setOf(ProximityTransition.ENTER),
        )
        fl.addProximityAlert(
            request,
            object : ProximityAlertListener {
                override fun onRegistered(alertId: String) {
                    zonesRegistered++
                    Log.i(TAG, "[ZONE] 등록: ${zone.name} id=$alertId")
                }

                override fun onTransition(event: ProximityAlertEvent) {
                    Log.i(TAG, "[ZONE] ${zone.name} ${event.transition}")
                    if (event.transition == ProximityTransition.ENTER) {
                        handler.post { zoneListener?.invoke(zone.name) }
                    }
                }

                override fun onError(exception: Exception) {
                    Log.w(TAG, "[ZONE] ${zone.name} 오류: $exception")
                }
            },
        )
    }

    // ------------------------------------------------------------------ 변환

    @Suppress("DEPRECATION")
    private fun VehicleGear?.toGear(): Gear? = when (this) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        VehicleGear.GEAR_UNKNOWN, null -> null
        else -> Gear.DRIVE // GEAR_DRIVE, GEAR_1..9
    }

    /** AOSP VehicleGear 값: NEUTRAL=1, REVERSE=2, PARK=4, DRIVE=8. 에뮬레이터에서 Park=4 확인. */
    private fun Int?.toGearFromAaos(): Gear? = when (this) {
        1 -> Gear.NEUTRAL
        2 -> Gear.REVERSE
        4 -> Gear.PARK
        8 -> Gear.DRIVE
        null, 0 -> null
        else -> if (this >= 16) Gear.DRIVE else null // 1단~9단은 Drive 계열
    }

    companion object {
        private const val TAG = "DriveCash"
        private const val PERM_FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
        private const val AAOS_POLL_MS = 500L
        private const val VEHICLE_REGISTER_DELAY_MS = 2_500L
        private const val FUSED_RETRY_MS = 2_000L
        private const val FUSED_RETRY_MAX = 15
    }
}
