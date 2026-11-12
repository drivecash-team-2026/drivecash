package com.drivecash.app.domain

/** 기어 상태. 차량 신호에서 얻지 못하면 [UNKNOWN]. */
enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/** 주행 모드(Drive Mode)와 정차 모드(Park Mode). 기획서의 Safety-First UI 분리 기준. */
enum class Mode { PARKED, DRIVING }

/** 1초 간격으로 수집한 차량 신호 스냅샷. 값을 얻지 못한 신호는 null. */
data class Sample(
    val timeMs: Long,
    val speedMps: Float?,
    val gear: Gear,
    val odometerKm: Float?,
    val brakePressed: Boolean?,
    val speedLimitKmh: Int?,
)

/** 주행 중 화면에 표시하는 값. */
data class LiveTrip(
    val distanceKm: Double,
    val points: Int,
    val safetyScore: Int,
    val speedKmh: Int,
    val speedLimitKmh: Int?,
    val elapsedSec: Int,
    val harshAccel: Int,
    val harshBrake: Int,
)

/** 한 번의 주행(P단 → 주행 → P단) 결과. */
data class TripReport(
    val startMs: Long,
    val endMs: Long,
    val distanceKm: Double,
    /** 계기판 주행거리(Odometer) 차이. 신호가 없거나 갱신되지 않으면 null. 검증용 교차 확인 값. */
    val odometerDeltaKm: Double?,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val harshAccel: Int,
    val harshBrake: Int,
    val speedingSec: Int,
    val safetyScore: Int,
    val distancePoints: Int,
    val spotBonusPoints: Int,
    val zoneBonusPoints: Int,
    val safeBonusPoints: Int,
    /** 데일리 미션 보너스. 엔진이 아니라 저장소가 오늘 누적 기록을 보고 채운다. */
    val missionBonusPoints: Int = 0,
) {
    val totalPoints: Int
        get() = distancePoints + spotBonusPoints + zoneBonusPoints + safeBonusPoints + missionBonusPoints
}

enum class HarshKind { ACCEL, BRAKE }

sealed interface EngineEvent {
    data class TripStarted(val timeMs: Long) : EngineEvent
    data class Harsh(val kind: HarshKind, val accelMps2: Double) : EngineEvent
    data class SpotBonus(val points: Int) : EngineEvent
    data class ZoneBonus(val zone: String, val points: Int) : EngineEvent
    data class Ignored(val reason: String) : EngineEvent
    data class TripEnded(val report: TripReport) : EngineEvent
    data class TripDiscarded(val distanceKm: Double) : EngineEvent
}
