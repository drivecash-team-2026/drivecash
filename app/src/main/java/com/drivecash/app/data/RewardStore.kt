package com.drivecash.app.data

import android.content.Context
import android.util.Log
import com.drivecash.app.domain.RewardPolicy
import com.drivecash.app.domain.TripReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.random.Random

/** 사용처 항목. 실제 연동이 아니라 시연용 목업이다. (docs/DEVELOPMENT.md V-11) */
data class RewardItem(val id: String, val title: String, val description: String, val cost: Int)

data class Voucher(val code: String, val title: String, val cost: Int, val issuedMs: Long)

data class RewardState(
    val balance: Int = 0,
    val streakDays: Int = 0,
    val attendedToday: Boolean = false,
    val missionDoneToday: Boolean = false,
    val todayKm: Double = 0.0,
    val trips: List<TripReport> = emptyList(),
    val vouchers: List<Voucher> = emptyList(),
)

sealed interface AttendResult {
    data class Ok(val points: Int, val streakDays: Int) : AttendResult
    data object Already : AttendResult
}

class RewardStore(context: Context, private val today: () -> LocalDate = { LocalDate.now() }) {

    private val prefs = context.applicationContext.getSharedPreferences("drivecash", Context.MODE_PRIVATE)
    private val zone = ZoneId.systemDefault()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<RewardState> = _state.asStateFlow()

    val catalog: List<RewardItem> = listOf(
        RewardItem("fuel5000", "주유 할인권 5,000원", "현대오일뱅크 / GS칼텍스 (시연용)", 5_000),
        RewardItem("charge3000", "EV 충전 할인권 3,000원", "충전 사업자 제휴 (시연용)", 3_000),
        RewardItem("shop10000", "현대샵 모바일 상품권 10,000원", "마이현대 연동 (시연용)", 10_000),
        RewardItem("parking2000", "주차 할인권 2,000원", "모두의주차장 (시연용)", 2_000),
        RewardItem("bluehands10000", "블루핸즈 정비비 10,000원 차감", "방문 정비 시 바코드 제시 (시연용)", 10_000),
    )

    // ------------------------------------------------------------------ 출석

    fun attend(): AttendResult {
        val now = today()
        val last = prefs.getString(KEY_LAST_ATTEND, null)?.let(LocalDate::parse)
        if (last == now) return AttendResult.Already
        val streak = if (last == now.minusDays(1)) prefs.getInt(KEY_STREAK, 0) + 1 else 1
        val bonus = (streak.coerceAtMost(RewardPolicy.STREAK_MAX_DAYS) - 1) * RewardPolicy.STREAK_BONUS_PER_DAY
        val points = RewardPolicy.ATTENDANCE_POINTS + bonus
        prefs.edit()
            .putString(KEY_LAST_ATTEND, now.toString())
            .putInt(KEY_STREAK, streak)
            .putLong(KEY_BALANCE, (balance() + points).toLong())
            .apply()
        publish()
        Log.i(TAG, "[ATTEND] +$points (연속 ${streak}일) 잔액=${balance()}")
        return AttendResult.Ok(points, streak)
    }

    // ------------------------------------------------------------------ 주행 완료

    /** 주행 결과를 저장하고 데일리 미션 보너스를 반영한 최종 리포트를 돌려준다. */
    fun finishTrip(report: TripReport): TripReport {
        val now = today()
        val todayKmBefore = todayKm(now)
        val missionDone = prefs.getString(KEY_MISSION_DONE, null) == now.toString()
        val reachedKm = todayKmBefore + report.distanceKm >= RewardPolicy.MISSION_KM
        val mission = if (!missionDone && reachedKm && report.safetyScore >= RewardPolicy.SAFE_SCORE_THRESHOLD) {
            RewardPolicy.MISSION_POINTS
        } else 0

        val finalReport = report.copy(missionBonusPoints = mission)
        val trips = JSONArray(prefs.getString(KEY_TRIPS, "[]")).apply { put(finalReport.toJson()) }
        val trimmed = JSONArray().apply {
            for (i in maxOf(0, trips.length() - MAX_TRIPS) until trips.length()) put(trips.get(i))
        }
        val editor = prefs.edit()
            .putString(KEY_TRIPS, trimmed.toString())
            .putLong(KEY_BALANCE, (balance() + finalReport.totalPoints).toLong())
        if (mission > 0) editor.putString(KEY_MISSION_DONE, now.toString())
        editor.apply()
        publish()
        Log.i(
            TAG,
            "[POINTS] +${finalReport.totalPoints} (거리 ${finalReport.distancePoints}, 스팟 ${finalReport.spotBonusPoints}, " +
                "구간 ${finalReport.zoneBonusPoints}, 안전 ${finalReport.safeBonusPoints}, 미션 ${finalReport.missionBonusPoints}) 잔액=${balance()}",
        )
        return finalReport
    }

    // ------------------------------------------------------------------ 사용

    /** 포인트를 차감하고 바우처를 발급한다. 잔액이 부족하면 null. */
    fun redeem(item: RewardItem): Voucher? {
        val bal = balance()
        if (bal < item.cost) return null
        val code = "DC-" + (1..8).map { CODE_CHARS[Random.nextInt(CODE_CHARS.length)] }.joinToString("")
        val voucher = Voucher(code, item.title, item.cost, System.currentTimeMillis())
        val list = JSONArray(prefs.getString(KEY_VOUCHERS, "[]")).apply { put(voucher.toJson()) }
        prefs.edit()
            .putLong(KEY_BALANCE, (bal - item.cost).toLong())
            .putString(KEY_VOUCHERS, list.toString())
            .apply()
        publish()
        Log.i(TAG, "[REDEEM] ${item.title} -${item.cost} 코드=$code 잔액=${balance()}")
        return voucher
    }

    /** 시연용: 교환 화면을 보여주기 위해 포인트를 임의로 지급한다. 실제 서비스에는 없어야 하는 기능이다. */
    fun grantDemoPoints(points: Int) {
        prefs.edit().putLong(KEY_BALANCE, (balance() + points).toLong()).apply()
        publish()
        Log.i(TAG, "[DEMO] 시연용 포인트 +$points 잔액=${balance()}")
    }

    /**
     * 저장된 값으로 화면 상태를 다시 계산한다. 자정이 지나면 "오늘 출석", "오늘 주행거리", 미션 상태가
     * 바뀌어야 하는데, 상태는 저장할 때만 갱신되므로 날짜가 바뀐 것을 감지한 쪽에서 호출한다.
     */
    fun refresh() = publish()

    /** 시연 초기화 */
    fun reset() {
        prefs.edit().clear().apply()
        publish()
        Log.i(TAG, "[RESET] 저장 데이터 초기화")
    }

    // ------------------------------------------------------------------ 내부

    private fun balance(): Int = prefs.getLong(KEY_BALANCE, 0L).toInt()

    private fun publish() {
        _state.value = load()
    }

    private fun todayKm(now: LocalDate): Double = loadTrips()
        .filter { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() == now }
        .sumOf { it.distanceKm }

    private fun load(): RewardState {
        val now = today()
        val last = prefs.getString(KEY_LAST_ATTEND, null)?.let(LocalDate::parse)
        val streak = when (last) {
            now, now.minusDays(1) -> prefs.getInt(KEY_STREAK, 0)
            else -> 0
        }
        return RewardState(
            balance = balance(),
            streakDays = streak,
            attendedToday = last == now,
            missionDoneToday = prefs.getString(KEY_MISSION_DONE, null) == now.toString(),
            todayKm = todayKm(now),
            trips = loadTrips().reversed(),
            vouchers = loadVouchers().reversed(),
        )
    }

    private fun loadTrips(): List<TripReport> {
        val arr = JSONArray(prefs.getString(KEY_TRIPS, "[]"))
        return (0 until arr.length()).map { arr.getJSONObject(it).toTrip() }
    }

    private fun loadVouchers(): List<Voucher> {
        val arr = JSONArray(prefs.getString(KEY_VOUCHERS, "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Voucher(o.getString("code"), o.getString("title"), o.getInt("cost"), o.getLong("issuedMs"))
        }
    }

    private fun Voucher.toJson() = JSONObject()
        .put("code", code).put("title", title).put("cost", cost).put("issuedMs", issuedMs)

    private fun TripReport.toJson() = JSONObject()
        .put("startMs", startMs).put("endMs", endMs).put("distanceKm", distanceKm)
        .put("odometerDeltaKm", odometerDeltaKm ?: JSONObject.NULL)
        .put("avgSpeedKmh", avgSpeedKmh).put("maxSpeedKmh", maxSpeedKmh)
        .put("harshAccel", harshAccel).put("harshBrake", harshBrake).put("speedingSec", speedingSec)
        .put("safetyScore", safetyScore).put("distancePoints", distancePoints)
        .put("spotBonusPoints", spotBonusPoints).put("zoneBonusPoints", zoneBonusPoints)
        .put("safeBonusPoints", safeBonusPoints).put("missionBonusPoints", missionBonusPoints)

    private fun JSONObject.toTrip() = TripReport(
        startMs = getLong("startMs"),
        endMs = getLong("endMs"),
        distanceKm = getDouble("distanceKm"),
        odometerDeltaKm = if (isNull("odometerDeltaKm")) null else getDouble("odometerDeltaKm"),
        avgSpeedKmh = getDouble("avgSpeedKmh"),
        maxSpeedKmh = getDouble("maxSpeedKmh"),
        harshAccel = getInt("harshAccel"),
        harshBrake = getInt("harshBrake"),
        speedingSec = getInt("speedingSec"),
        safetyScore = getInt("safetyScore"),
        distancePoints = getInt("distancePoints"),
        spotBonusPoints = getInt("spotBonusPoints"),
        zoneBonusPoints = getInt("zoneBonusPoints"),
        safeBonusPoints = getInt("safeBonusPoints"),
        missionBonusPoints = getInt("missionBonusPoints"),
    )

    companion object {
        const val TAG = "DriveCash"
        private const val KEY_BALANCE = "balance"
        private const val KEY_LAST_ATTEND = "lastAttend"
        private const val KEY_STREAK = "streak"
        private const val KEY_MISSION_DONE = "missionDone"
        private const val KEY_TRIPS = "trips"
        private const val KEY_VOUCHERS = "vouchers"
        private const val MAX_TRIPS = 30
        private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun formatPoints(p: Int): String = String.format(Locale.KOREA, "%,d", p)
    }
}
