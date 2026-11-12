package com.drivecash.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivecash.app.data.AttendResult
import com.drivecash.app.data.RewardItem
import com.drivecash.app.data.RewardState
import com.drivecash.app.data.RewardStore
import com.drivecash.app.data.Voucher
import com.drivecash.app.data.signals.BonusZone
import com.drivecash.app.data.signals.PleosVehicleSignals
import com.drivecash.app.data.signals.VehicleSignals
import com.drivecash.app.domain.DriveEngine
import com.drivecash.app.domain.EngineEvent
import com.drivecash.app.domain.Gear
import com.drivecash.app.domain.HarshKind
import com.drivecash.app.domain.LiveTrip
import com.drivecash.app.domain.Mode
import com.drivecash.app.domain.NearbyPlace
import com.drivecash.app.domain.Places
import com.drivecash.app.domain.TripReport
import com.drivecash.app.tts.Announcer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val mode: Mode = Mode.PARKED,
    val gear: Gear = Gear.UNKNOWN,
    val speedKmh: Int = 0,
    val live: LiveTrip? = null,
    val rewards: RewardState = RewardState(),
    val pendingReport: TripReport? = null,
    val newVoucher: Voucher? = null,
    /** 주행 중 잠깐 보여주는 알림 (스팟 보너스 등) */
    val toast: String? = null,
    /** 정차 화면의 안내 문구 (출석 결과 등) */
    val notice: String? = null,
    val status: String = "",
    /** 현재 위치에서 종류별로 가장 가까운 사용처. 위치를 모르면 null */
    val nearby: List<NearbyPlace>? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val signals: VehicleSignals = PleosVehicleSignals(app, DemoZones.all)
    private val engine = DriveEngine()
    private val store = RewardStore(app)
    private val announcer = Announcer(app)

    private val _ui = MutableStateFlow(UiState(rewards = store.state.value))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val catalog: List<RewardItem> get() = store.catalog

    private var toastJob: Job? = null
    private var tickCount = 0

    init {
        announcer.start()
        signals.setZoneListener { zone -> handleEvents(engine.onZoneEntered(zone)) }
        signals.start()

        viewModelScope.launch {
            store.state.collect { r -> _ui.update { it.copy(rewards = r) } }
        }
        viewModelScope.launch {
            while (true) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    private var lastDate: java.time.LocalDate = java.time.LocalDate.now()

    private fun tick() {
        val date = java.time.LocalDate.now()
        if (date != lastDate) {
            Log.i(TAG, "[DATE] 날짜 변경 $lastDate -> $date, 출석·미션 상태를 다시 계산합니다")
            lastDate = date
            store.refresh()
        }
        val sample = signals.snapshot(System.currentTimeMillis())
        val before = engine.mode
        logAcceleration(sample.timeMs, sample.speedMps)
        val events = engine.onSample(sample)
        handleEvents(events)

        val live = engine.live()
        val nearby = nearbyIfParked()
        _ui.update {
            it.copy(
                mode = engine.mode,
                gear = sample.gear,
                speedKmh = ((sample.speedMps ?: 0f) * 3.6f).toInt(),
                live = live,
                status = signals.statusSummary(),
                nearby = nearby,
            )
        }

        if (before != engine.mode) {
            Log.i(TAG, "[MODE] ${before} -> ${engine.mode} (gear=${sample.gear}, speed=${sample.speedMps})")
        }
        tickCount++
        if (live != null && tickCount % LIVE_LOG_EVERY == 0) {
            Log.i(
                TAG,
                "[LIVE] ${"%.3f".format(live.distanceKm)}km ${live.points}P 점수=${live.safetyScore} " +
                    "속도=${live.speedKmh} 제한=${live.speedLimitKmh ?: "-"} 급가속=${live.harshAccel} 급제동=${live.harshBrake}",
            )
        }
    }

    private var lastNearbyKey: Pair<Double, Double>? = null
    private var lastNearby: List<NearbyPlace>? = null

    /** 정차 중에만 계산하고, 위치가 바뀐 경우에만 다시 계산한다. 주행 중에는 화면에 쓰지 않으므로 null. */
    private fun nearbyIfParked(): List<NearbyPlace>? {
        if (engine.mode != Mode.PARKED) return null
        val loc = signals.currentLocation() ?: return null
        if (loc != lastNearbyKey) {
            lastNearbyKey = loc
            lastNearby = Places.nearestPerKind(loc.first, loc.second)
            Log.i(TAG, "[PLACES] 위치 ${"%.4f".format(loc.first)}, ${"%.4f".format(loc.second)} -> " +
                lastNearby!!.joinToString(", ") { "${it.place.name} ${Places.formatDistance(it.distanceMeters)}" })
        }
        return lastNearby
    }

    private var prevSpeedMps: Float? = null
    private var prevSpeedAtMs = 0L

    /** 급가속·급제동 기준을 현실에 맞게 조정할 수 있도록, 가속도가 눈에 띄는 틱의 값을 남긴다. */
    private fun logAcceleration(nowMs: Long, speedMps: Float?) {
        val prev = prevSpeedMps
        if (speedMps != null && prev != null && nowMs > prevSpeedAtMs) {
            val a = (speedMps - prev) / ((nowMs - prevSpeedAtMs) / 1000.0)
            if (kotlin.math.abs(a) >= ACCEL_LOG_THRESHOLD) {
                Log.i(TAG, "[ACCEL] ${"%.2f".format(a)}m/s² (${"%.1f".format(prev * 3.6)} -> ${"%.1f".format(speedMps * 3.6)}km/h)")
            }
        }
        prevSpeedMps = speedMps
        prevSpeedAtMs = nowMs
    }

    private fun handleEvents(events: List<EngineEvent>) {
        for (e in events) when (e) {
            is EngineEvent.TripStarted -> {
                Log.i(TAG, "[TRIP] 시작")
                announcer.say("주행을 시작합니다. 안전운전 하세요.")
            }
            is EngineEvent.SpotBonus -> {
                Log.i(TAG, "[BONUS] 스팟 보너스 +${e.points}")
                showToast("안전운전 스팟 보너스 +${e.points}P")
                announcer.say("안전운전 보너스 ${e.points}포인트가 적립되었습니다.")
            }
            is EngineEvent.ZoneBonus -> {
                Log.i(TAG, "[BONUS] 구간 보너스 ${e.zone} +${e.points}")
                showToast("${e.zone} 진입 +${e.points}P")
                announcer.say("${e.zone}에 진입했습니다. ${e.points}포인트가 적립되었습니다.")
            }
            is EngineEvent.Harsh -> {
                val kind = if (e.kind == HarshKind.ACCEL) "급가속" else "급제동"
                Log.i(TAG, "[HARSH] $kind ${"%.1f".format(e.accelMps2)}m/s²")
                showToast("$kind 감지")
            }
            is EngineEvent.Ignored -> Log.i(TAG, "[IGNORED] ${e.reason}")
            is EngineEvent.TripDiscarded ->
                Log.i(TAG, "[TRIP] 너무 짧아 기록하지 않음 (${"%.3f".format(e.distanceKm)}km)")
            is EngineEvent.TripEnded -> {
                val final = store.finishTrip(e.report)
                Log.i(
                    TAG,
                    "[REPORT] ${"%.2f".format(final.distanceKm)}km 평균=${"%.1f".format(final.avgSpeedKmh)} " +
                        "최고=${"%.1f".format(final.maxSpeedKmh)} 점수=${final.safetyScore} " +
                        "급가속=${final.harshAccel} 급제동=${final.harshBrake} 과속=${final.speedingSec}s " +
                        "주행거리계Δ=${final.odometerDeltaKm?.let { "%.3f".format(it) } ?: "-"} 합계=${final.totalPoints}P",
                )
                _ui.update { it.copy(pendingReport = final) }
            }
        }
    }

    // ------------------------------------------------------------------ 사용자 동작 (정차 중에만)

    fun attend() {
        if (engine.mode != Mode.PARKED) return
        val msg = when (val r = store.attend()) {
            is AttendResult.Ok -> "출석 완료 +${r.points}P (연속 ${r.streakDays}일)"
            AttendResult.Already -> "오늘은 이미 출석했습니다"
        }
        _ui.update { it.copy(notice = msg) }
    }

    fun redeem(item: RewardItem) {
        if (engine.mode != Mode.PARKED) return
        val v = store.redeem(item)
        _ui.update {
            if (v == null) it.copy(notice = "포인트가 부족합니다")
            else it.copy(notice = "${item.title} 교환 완료", newVoucher = v)
        }
    }

    fun dismissReport() = _ui.update { it.copy(pendingReport = null) }

    fun dismissVoucher() = _ui.update { it.copy(newVoucher = null) }

    fun resetDemo() = store.reset()

    fun grantDemoPoints() {
        if (engine.mode != Mode.PARKED) return
        store.grantDemoPoints(DEMO_GRANT_POINTS)
        _ui.update { it.copy(notice = "시연용 포인트 +${RewardStore.formatPoints(DEMO_GRANT_POINTS)}P 지급") }
    }

    private fun showToast(text: String) {
        _ui.update { it.copy(toast = text) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(TOAST_MS)
            _ui.update { it.copy(toast = null) }
        }
    }

    override fun onCleared() {
        signals.stop()
        announcer.stop()
        super.onCleared()
    }

    companion object {
        private const val TAG = "DriveCash"
        private const val TICK_MS = 1_000L
        private const val TOAST_MS = 4_000L
        private const val LIVE_LOG_EVERY = 5
        private const val ACCEL_LOG_THRESHOLD = 1.0
        private const val DEMO_GRANT_POINTS = 10_000
    }
}

/** 스팟 구간 시연용 좌표. 실제 서비스에서는 서버/제휴 데이터로 교체한다. (docs/DEVELOPMENT.md V-10) */
object DemoZones {
    val all = listOf(
        BonusZone("스팟 구간 A", 37.4300, -122.0800, 300f),
        BonusZone("스팟 구간 B", 37.4400, -122.0700, 300f),
    )
}
