package com.drivecash.app.domain

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 1초 간격의 [Sample]을 받아 주행/정차 모드 전환, 주행 거리, 안전운전 점수, 포인트를 계산한다.
 * Android 의존성이 없는 순수 로직이다.
 *
 * 거리 계산: 속도의 사다리꼴 적분. 계기판 주행거리(Odometer)는 교차 확인용으로만 기록한다.
 * 에뮬레이터는 속도로 주행거리를 적분하지 않고, 실차 계기판의 해상도는 확인되지 않았기 때문이다. (D-020)
 */
class DriveEngine {

    var mode: Mode = Mode.PARKED
        private set

    private var trip: TripState? = null
    private var stoppedSec = 0.0

    /** 현재 주행 중이면 화면용 값, 아니면 null. */
    fun live(): LiveTrip? = trip?.toLive()

    fun onSample(s: Sample): List<EngineEvent> {
        val speed = (s.speedMps ?: 0f).coerceAtLeast(0f)
        val events = mutableListOf<EngineEvent>()

        val prevT = trip?.lastTimeMs
        if (speed < 0.5f) {
            stoppedSec += if (prevT != null) (s.timeMs - prevT) / 1000.0 else 1.0
        } else {
            stoppedSec = 0.0
        }

        val wanted = desiredMode(s, speed)
        when {
            mode == Mode.PARKED && wanted == Mode.DRIVING -> {
                mode = Mode.DRIVING
                trip = TripState(s.timeMs, s.odometerKm, speed)
                events += EngineEvent.TripStarted(s.timeMs)
            }
            mode == Mode.DRIVING && wanted == Mode.PARKED -> {
                mode = Mode.PARKED
                val t = trip
                trip = null
                if (t != null) {
                    t.advance(s, speed, events)
                    events += if (t.distanceKm >= RewardPolicy.MIN_TRIP_KM) {
                        EngineEvent.TripEnded(t.toReport(s))
                    } else {
                        EngineEvent.TripDiscarded(t.distanceKm)
                    }
                }
            }
            mode == Mode.DRIVING -> trip?.advance(s, speed, events)
        }
        return events
    }

    /** 스팟 구간 진입. 주행 중에 구간마다 1회만 보너스를 준다. */
    fun onZoneEntered(zone: String): List<EngineEvent> {
        val t = trip ?: return emptyList()
        if (!t.zonesHit.add(zone)) return emptyList()
        t.zoneBonus += RewardPolicy.ZONE_BONUS_POINTS
        return listOf(EngineEvent.ZoneBonus(zone, RewardPolicy.ZONE_BONUS_POINTS))
    }

    private fun desiredMode(s: Sample, speed: Float): Mode = when (s.gear) {
        Gear.PARK -> Mode.PARKED
        Gear.DRIVE, Gear.REVERSE -> Mode.DRIVING
        Gear.NEUTRAL -> mode
        Gear.UNKNOWN -> when {
            speed > 1f -> Mode.DRIVING
            stoppedSec >= RewardPolicy.UNKNOWN_GEAR_PARK_AFTER_SEC -> Mode.PARKED
            else -> mode
        }
    }

    private class TripState(val startMs: Long, val startOdoKm: Float?, startSpeed: Float) {
        var lastTimeMs = startMs
        var lastSpeed = startSpeed
        var lastOdoKm: Float? = startOdoKm
        var distanceKm = 0.0
        var elapsedSec = 0.0
        var speedingSec = 0.0
        var maxSpeedKmh = 0.0
        var harshAccel = 0
        var harshBrake = 0
        var spotBonus = 0
        var zoneBonus = 0
        var cleanKm = 0.0
        /** 직전 급가속·급제동 시각. 0 = 없음. (Long.MIN_VALUE 를 쓰면 뺄셈이 오버플로해서 항상 쿨다운 중으로 판정된다) */
        var lastHarshMs = 0L
        var speedLimitKmh: Int? = null
        var speedKmh = 0
        val zonesHit = mutableSetOf<String>()

        fun advance(s: Sample, speed: Float, events: MutableList<EngineEvent>) {
            val dt = (s.timeMs - lastTimeMs) / 1000.0
            if (dt <= 0.0) return
            val step = dt.coerceAtMost(5.0)

            val tickKm = (lastSpeed + speed) / 2.0 * step / 1000.0
            distanceKm += tickKm
            elapsedSec += step

            val speedKmhNow = speed * 3.6
            speedKmh = speedKmhNow.roundToInt()
            maxSpeedKmh = max(maxSpeedKmh, speedKmhNow)
            speedLimitKmh = s.speedLimitKmh?.takeIf { it > 0 }

            var clean = true
            val limit = speedLimitKmh
            if (limit != null && speedKmhNow > limit * RewardPolicy.SPEEDING_TOLERANCE) {
                speedingSec += step
                clean = false
            }

            val accel = (speed - lastSpeed) / step
            when {
                kotlin.math.abs(accel) > RewardPolicy.MAX_PLAUSIBLE_MPS2 ->
                    events += EngineEvent.Ignored("가속도 ${"%.1f".format(accel)}m/s² 는 신호 점프로 판단해 무시")
                s.timeMs - lastHarshMs < RewardPolicy.HARSH_COOLDOWN_MS -> Unit
                accel >= RewardPolicy.HARSH_ACCEL_MPS2 -> {
                    harshAccel++; lastHarshMs = s.timeMs; clean = false
                    events += EngineEvent.Harsh(HarshKind.ACCEL, accel)
                }
                accel <= RewardPolicy.HARSH_BRAKE_MPS2 -> {
                    harshBrake++; lastHarshMs = s.timeMs; clean = false
                    events += EngineEvent.Harsh(HarshKind.BRAKE, accel)
                }
            }

            if (clean) cleanKm += tickKm else cleanKm = 0.0
            while (cleanKm >= RewardPolicy.SPOT_BONUS_KM) {
                cleanKm -= RewardPolicy.SPOT_BONUS_KM
                spotBonus += RewardPolicy.SPOT_BONUS_POINTS
                events += EngineEvent.SpotBonus(RewardPolicy.SPOT_BONUS_POINTS)
            }

            lastTimeMs = s.timeMs
            lastSpeed = speed
            s.odometerKm?.let { lastOdoKm = it }
        }

        fun score(): Int {
            if (elapsedSec <= 0.0) return 100
            val harshPenalty = (harshAccel + harshBrake) * RewardPolicy.PENALTY_PER_HARSH_EVENT
            val speedingPenalty = (RewardPolicy.PENALTY_SPEEDING_MAX * (speedingSec / elapsedSec)).roundToInt()
            return (100 - harshPenalty - speedingPenalty).coerceIn(0, 100)
        }

        fun distancePoints(): Int = (distanceKm * RewardPolicy.POINTS_PER_KM).toInt()

        fun toLive() = LiveTrip(
            distanceKm = distanceKm,
            points = distancePoints() + spotBonus + zoneBonus,
            safetyScore = score(),
            speedKmh = speedKmh,
            speedLimitKmh = speedLimitKmh,
            elapsedSec = elapsedSec.toInt(),
            harshAccel = harshAccel,
            harshBrake = harshBrake,
        )

        fun toReport(end: Sample): TripReport {
            val score = score()
            val base = distancePoints()
            val safeBonus = if (score >= RewardPolicy.SAFE_SCORE_THRESHOLD) {
                (base * RewardPolicy.SAFE_BONUS_RATE).toInt()
            } else 0
            val odoDelta = if (startOdoKm != null && lastOdoKm != null) (lastOdoKm!! - startOdoKm).toDouble() else null
            return TripReport(
                startMs = startMs,
                endMs = end.timeMs,
                distanceKm = distanceKm,
                odometerDeltaKm = odoDelta,
                avgSpeedKmh = if (elapsedSec > 0) distanceKm / (elapsedSec / 3600.0) else 0.0,
                maxSpeedKmh = maxSpeedKmh,
                harshAccel = harshAccel,
                harshBrake = harshBrake,
                speedingSec = speedingSec.roundToInt(),
                safetyScore = score,
                distancePoints = base,
                spotBonusPoints = spotBonus,
                zoneBonusPoints = zoneBonus,
                safeBonusPoints = safeBonus,
            )
        }
    }
}
