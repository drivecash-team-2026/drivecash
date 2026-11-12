package com.drivecash.app.verify

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.InvocationTargetException
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * docs/DEVELOPMENT.md 4장의 V-항목을 실제 SDK 호출로 검증한다.
 * 결과는 화면(sink)과 Logcat(태그 [TAG])에 같은 형식으로 남긴다.
 *
 * 로그 형식: `[V-xx] 메시지`
 */
class SdkVerifier(
    private val context: Context,
    private val sink: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())

    private var vehicle: Vehicle? = null
    private var fusedLocation: FusedLocation? = null
    private var naviHelper: NaviHelper? = null
    private var tts: TextToSpeech? = null

    private var lastOdometerKm: Float? = null
    private var lastOdometerAt = 0L
    private var lastLocation: LocationData? = null
    private var locationUpdates = 0

    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            val now = System.currentTimeMillis()
            val prev = lastOdometerKm
            val speed = if (prev != null && value != null && lastOdometerAt > 0 && now > lastOdometerAt) {
                " (Δ=${"%.4f".format(value - prev)}km, 환산 ${"%.1f".format((value - prev) / ((now - lastOdometerAt) / 3_600_000.0))}km/h)"
            } else ""
            log("V-01", "odometer 갱신: $value km$speed")
            lastOdometerKm = value
            lastOdometerAt = now
        }

        override fun onFailed(e: Exception) = log("V-01", "odometer 리스너 실패: $e")
    }

    @Suppress("DEPRECATION")
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = log("V-04", "기어 갱신(Pleos Gear API): $gear")
        override fun onFailed(e: Exception) = log("V-04", "기어 리스너 실패: $e")
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) = log("V-03", "브레이크 페달 갱신: $status")
        override fun onFailed(e: Exception) = log("V-03", "브레이크 리스너 실패: $e")
    }

    private val fusedListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location == null) {
                log("V-07", "위치 null")
                return
            }
            locationUpdates++
            val prev = lastLocation
            val speed = if (prev != null && location.timestampMillis > prev.timestampMillis) {
                val m = distanceMeters(prev.latitude, prev.longitude, location.latitude, location.longitude)
                val s = (location.timestampMillis - prev.timestampMillis) / 1000.0
                " 환산 속도=${"%.1f".format(m / s * 3.6)}km/h"
            } else ""
            log(
                "V-07",
                "위치 #$locationUpdates lat=${location.latitude} lon=${location.longitude} " +
                    "heading=${location.heading} conf=${location.confidence}$speed",
            )
            lastLocation = location
        }

        override fun onError(exception: Exception) = log("V-07", "위치 오류: $exception")
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) = log("V-06", "제한속도: $speedLimit")
        override fun onNavigationStatus(status: NavigationStatus) = log("V-06", "내비 상태: $status")
        override fun onRouteStarted(routeStartInfo: ai.pleos.playground.navi.data.RouteStartInfo) =
            log("V-06", "경로 안내 시작: $routeStartInfo")
        override fun onRouteStateInfo(routeStateInfo: ai.pleos.playground.navi.data.RouteStateInfo) =
            log("V-06", "경로 상태: isRouting=${routeStateInfo.isRouting} mode=${routeStateInfo.naviMode}")
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) =
            log("V-13", "내비 위치: ${currentLocationInfo.latitude}, ${currentLocationInfo.longitude}")

        override fun onError(errorCode: ai.pleos.playground.navi.constants.NaviErrorCode) =
            log("V-06", "내비 오류: $errorCode")
    }

    // ------------------------------------------------------------------ 실행

    /** 모든 검증을 순서대로 실행한다. 마지막에 `VERIFY_DONE` 을 남긴다. */
    fun runAll(listenSeconds: Int = 12, routeTest: Boolean = false) {
        log("INIT", "검증 시작. 패키지=${context.packageName}")
        step(0) { connect() }
        step(2_000) { checkOdometer() }
        step(2_300) { checkGear() }
        step(2_600) { checkBrake() }
        step(3_000) { checkAaos() }
        step(3_500) { checkFusedLocation() }
        step(4_000) { checkNavi() }
        step(4_500) { checkTts() }
        if (routeTest) step(7_000) { requestDemoRoute() }
        step(4_500L + listenSeconds * 1000L) {
            log("INIT", "VERIFY_DONE")
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        runCatching { vehicle?.getOdometer()?.unregisterDistanceDriven(odometerListener) }
        runCatching { @Suppress("DEPRECATION") vehicle?.getGear()?.unregisterGearSelection(gearListener) }
        runCatching { vehicle?.getBrake()?.unregisterBrakePedalStatus(brakeListener) }
        runCatching { fusedLocation?.unregisterFusedLocationCallback(fusedListener) }
        runCatching { naviHelper?.removeListener(naviListener) }
        runCatching { vehicle?.release() }
        runCatching { fusedLocation?.release() }
        runCatching { naviHelper?.release() }
        runCatching { tts?.release() }
        vehicle = null
        fusedLocation = null
        naviHelper = null
        tts = null
    }

    private fun step(delayMs: Long, block: () -> Unit) {
        handler.postDelayed({
            try {
                block()
            } catch (t: Throwable) {
                log("INIT", "예외: ${t.javaClass.simpleName}: ${t.message}")
            }
        }, delayMs)
    }

    // ------------------------------------------------------------------ 검증 항목

    private fun connect() {
        vehicle = Vehicle(context).also { it.initialize() }
        log("INIT", "Vehicle.initialize() 호출")
    }

    /** V-01 */
    private fun checkOdometer() {
        val odo = vehicle?.getOdometer() ?: return log("V-01", "vehicle 없음")
        odo.checkCurrentDistanceDrivenCapabilities(
            onComplete = { cfg -> log("V-01", "capability accessType=${cfg.accessType}") },
            onFailed = { e -> log("V-01", "capability 실패: $e") },
        )
        odo.getCurrentDistanceDriven(
            onComplete = { km -> log("V-01", "getCurrentDistanceDriven=$km km") },
            onFailed = { e -> log("V-01", "get 실패: $e") },
        )
        odo.registerDistanceDriven(odometerListener)
        log("V-01", "registerDistanceDriven 등록")
    }

    /** V-04 (Pleos Gear API, deprecated) */
    @Suppress("DEPRECATION")
    private fun checkGear() {
        val gear = vehicle?.getGear() ?: return log("V-04", "vehicle 없음")
        gear.getCurrentGearSelectionState(
            onComplete = { g -> log("V-04", "getCurrentGearSelectionState=$g") },
            onFailed = { e -> log("V-04", "get 실패: $e") },
        )
        gear.registerGearSelection(gearListener)
        log("V-04", "registerGearSelection 등록")
    }

    /** V-03 보조 신호 */
    private fun checkBrake() {
        val brake = vehicle?.getBrake() ?: return log("V-03", "vehicle 없음")
        brake.checkBrakePedalStatusCapabilities(
            onComplete = { cfg -> log("V-03", "brake capability accessType=${cfg.accessType}") },
            onFailed = { e -> log("V-03", "brake capability 실패: $e") },
        )
        brake.getCurrentBrakePedalStatus(
            onComplete = { s -> log("V-03", "getCurrentBrakePedalStatus=$s") },
            onFailed = { e -> log("V-03", "brake get 실패: $e") },
        )
        brake.registerBrakePedalStatus(brakeListener)
    }

    /**
     * V-02, V-04: AAOS 표준 속성. android.car 는 공개 SDK에 없어서 리플렉션으로 호출한다.
     * PERF_VEHICLE_SPEED = 0x11600207, GEAR_SELECTION = 0x11400400 (VehiclePropertyIds)
     */
    private fun checkAaos() {
        val carClass = try {
            Class.forName("android.car.Car")
        } catch (e: ClassNotFoundException) {
            return log("V-02", "android.car.Car 클래스를 찾을 수 없음")
        }
        val car = try {
            carClass.getMethod("createCar", Context::class.java).invoke(null, context)
        } catch (e: InvocationTargetException) {
            return log("V-02", "Car.createCar 실패: ${e.targetException}")
        } catch (e: Exception) {
            return log("V-02", "Car.createCar 호출 불가: $e")
        }
        if (car == null) return log("V-02", "Car.createCar 가 null 반환")
        log("V-02", "Car.createCar 성공")
        step(1_500) {
            try {
                val connected = carClass.getMethod("isConnected").invoke(car)
                log("V-02", "Car.isConnected=$connected")
                val mgr = carClass.getMethod("getCarManager", String::class.java).invoke(car, "property")
                    ?: return@step log("V-02", "CarPropertyManager null")
                val mgrClass = mgr.javaClass
                listProperties(mgr)
                readAaos(mgr, mgrClass, "V-02", "PERF_VEHICLE_SPEED(m/s)", 0x11600207, isFloat = true)
                readAaos(mgr, mgrClass, "V-04", "GEAR_SELECTION", 0x11400400, isFloat = false)
                pollAaosSpeed(mgr, mgrClass)
            } catch (e: InvocationTargetException) {
                log("V-02", "AAOS 호출 실패: ${e.targetException}")
            }
        }
    }

    /** 속도가 시간에 따라 바뀌는지 확인하기 위해 1초마다 읽고, 값이 바뀔 때만 기록한다. (V-02, V-03) */
    private var lastAaosSpeed: Float? = null
    private var lastAaosSpeedAt = 0L

    private fun pollAaosSpeed(mgr: Any, mgrClass: Class<*>, remaining: Int = 40) {
        if (remaining <= 0) return
        try {
            val v = mgrClass.getMethod("getFloatProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(mgr, 0x11600207, 0) as Float
            val now = System.currentTimeMillis()
            val prev = lastAaosSpeed
            if (prev == null || prev != v) {
                val accel = if (prev != null && lastAaosSpeedAt > 0) {
                    val dt = (now - lastAaosSpeedAt) / 1000.0
                    " (Δ=${"%.2f".format(v - prev)}m/s, ${"%.2f".format((v - prev) / dt)}m/s²)"
                } else ""
                log("V-02", "AAOS 속도 변경: ${"%.2f".format(v)} m/s = ${"%.1f".format(v * 3.6)} km/h$accel")
                lastAaosSpeed = v
                lastAaosSpeedAt = now
            }
        } catch (e: Exception) {
            log("V-02", "AAOS 속도 폴링 실패: ${(e as? InvocationTargetException)?.targetException ?: e}")
            return
        }
        handler.postDelayed({ pollAaosSpeed(mgr, mgrClass, remaining - 1) }, 1_000)
    }

    private fun listProperties(mgr: Any) {
        val list = mgr.javaClass.getMethod("getPropertyList").invoke(mgr) as? List<*>
        if (list == null) {
            log("V-02", "getPropertyList 결과 없음")
            return
        }
        val ids = list.mapNotNull { cfg ->
            runCatching { cfg!!.javaClass.getMethod("getPropertyId").invoke(cfg) as Int }.getOrNull()
        }
        log("V-02", "AAOS 읽기 가능 속성 ${ids.size}개")
        ids.chunked(12).forEach { chunk -> log("V-02", "ids: " + chunk.joinToString(",") { "0x" + it.toString(16) }) }
        log("V-02", "PERF_VEHICLE_SPEED 존재=${ids.contains(0x11600207)}, GEAR_SELECTION 존재=${ids.contains(0x11400400)}")
    }

    private fun readAaos(mgr: Any, mgrClass: Class<*>, id: String, name: String, propId: Int, isFloat: Boolean) {
        try {
            val value = if (isFloat) {
                mgrClass.getMethod("getFloatProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(mgr, propId, 0)
            } else {
                mgrClass.getMethod("getIntProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(mgr, propId, 0)
            }
            log(id, "AAOS $name = $value")
        } catch (e: InvocationTargetException) {
            log(id, "AAOS $name 읽기 실패: ${e.targetException}")
        } catch (e: Exception) {
            log(id, "AAOS $name 호출 불가: $e")
        }
    }

    /** V-07 */
    private fun checkFusedLocation() {
        val fl = FusedLocation(context)
        fusedLocation = fl
        log("V-07", "isSupported=${fl.isSupported()}")
        val granted = context.checkSelfPermission(PERM_FUSED_LOCATION) == PackageManager.PERMISSION_GRANTED
        log("V-07", "FUSED_LOCATION 퍼미션 허용=$granted")
        if (!granted) return
        fl.initialize()
        step(1_000) {
            fl.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(location: LocationData?) = log("V-07", "lastKnown=$location")
                override fun onError(exception: Exception) = log("V-07", "lastKnown 오류: $exception")
            })
            fl.registerFusedLocationCallback(fusedListener)
            log("V-07", "registerFusedLocationCallback 등록")
        }
    }

    /** V-06, V-13 */
    private fun checkNavi() {
        val navi = NaviHelper(context)
        naviHelper = navi
        navi.addListener(naviListener)
        navi.initialize(
            onConnected = {
                log("V-06", "NaviHelper 연결됨")
                navi.getCurrentLocationInfo()
                navi.getRouteStateInfo()
            },
            onFailed = { t -> log("V-06", "NaviHelper 연결 실패: $t") },
        )
    }

    /**
     * V-06, V-13: 내비 경로 안내를 시작시키고 제한속도·상태·위치 콜백이 어떻게 바뀌는지 본다.
     * 목적지는 시연용 좌표다. 내비가 기본 지도 앱으로 설정되어 있어야 한다. (NaviHelper 샘플 README)
     */
    fun requestDemoRoute() {
        val navi = naviHelper ?: return log("V-06", "NaviHelper 가 아직 연결되지 않음")
        try {
            navi.requestRoute(
                ai.pleos.playground.navi.data.RouteInfo(
                    latitude = 37.4979,
                    longitude = 127.0276,
                    poiName = "강남역",
                    poiId = "demo-gangnam",
                    address = "서울특별시 강남구 강남대로 396",
                    poiSubId = "0",
                ),
            )
            log("V-06", "requestRoute 호출: 강남역 (37.4979, 127.0276)")
        } catch (t: Throwable) {
            log("V-06", "requestRoute 실패: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** V-08 */
    private fun checkTts() {
        val engine = TextToSpeech(context, Mode.ON_DEVICE)
        tts = engine
        // addEventListener 는 initialize 이후에 호출해야 한다.
        // (먼저 호출하면 SDK 내부 lateinit 프로퍼티 때문에 UninitializedPropertyAccessException 발생)
        engine.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                log("V-08", "TTS 초기화 성공")
                engine.addEventListener(object : EventListener {
                    override fun onStart() = log("V-08", "TTS onStart")
                    override fun onReady() = log("V-08", "TTS onReady")
                    override fun onDone() = log("V-08", "TTS onDone")
                    override fun onError(errorType: ErrorType) = log("V-08", "TTS onError: $errorType")
                    override fun onStop() = log("V-08", "TTS onStop")
                    override fun onUpdatedRms(rms: Double) = Unit
                })
                // 두 번째 인자는 SDK 로그의 isFinal 에 해당한다. true 로 해야 종료 이벤트가 오는지 시험한다.
                engine.speak("드라이브캐시 음성 안내 테스트입니다.", true)
            }

            override fun onFailure(errorType: ErrorType) = log("V-08", "TTS 초기화 실패: $errorType")
        })
    }

    // ------------------------------------------------------------------ 공통

    private fun log(id: String, message: String) {
        val line = "[$id] $message"
        Log.i(TAG, line)
        handler.post { sink(line) }
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    companion object {
        const val TAG = "DriveCashVerify"
        const val PERM_FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
    }
}
