package com.drivecash.app.domain

/**
 * 포인트와 안전운전 판정에 쓰는 모든 기준값.
 *
 * 이 값들은 기획서에 수치가 없어서 정한 **가정**이다. (docs/DEVELOPMENT.md D-020)
 * 실차 데이터로 검증하기 전까지는 임의의 시작값이므로 이 파일 한 곳에서만 바꾼다.
 */
object RewardPolicy {
    // ---- 적립 ----
    const val POINTS_PER_KM = 10.0
    /** 급가속·급제동·과속 없이 이 거리를 주행할 때마다 스팟 보너스 */
    const val SPOT_BONUS_KM = 0.5
    const val SPOT_BONUS_POINTS = 30
    /** 주행 종료 시 안전운전 점수가 이 값 이상이면 거리 포인트의 일부를 추가 지급 */
    const val SAFE_SCORE_THRESHOLD = 90
    const val SAFE_BONUS_RATE = 0.5
    /** 스팟 구간(근접 알림) 진입 보너스. 구간당 주행 1회 */
    const val ZONE_BONUS_POINTS = 20

    // ---- 출석 / 미션 ----
    const val ATTENDANCE_POINTS = 50
    const val STREAK_BONUS_PER_DAY = 10
    const val STREAK_MAX_DAYS = 7
    /** 오늘 누적 거리가 이 값 이상이고 해당 주행 점수가 기준 이상이면 1회 지급 */
    const val MISSION_KM = 1.0
    const val MISSION_POINTS = 100

    // ---- 안전운전 판정 ----
    /** m/s². 이 값 이상의 가속을 급가속으로 본다. */
    const val HARSH_ACCEL_MPS2 = 2.8
    /** m/s². 이 값 이하(음수)의 감속을 급제동으로 본다. */
    const val HARSH_BRAKE_MPS2 = -3.0
    /** 이보다 큰 가속도는 실제 차량에서 나올 수 없으므로 신호 점프(에뮬레이터 계단 입력 등)로 보고 무시한다. */
    const val MAX_PLAUSIBLE_MPS2 = 12.0
    const val HARSH_COOLDOWN_MS = 3_000L
    /** 제한속도의 이 배수를 넘으면 과속 */
    const val SPEEDING_TOLERANCE = 1.10
    const val PENALTY_PER_HARSH_EVENT = 6
    const val PENALTY_SPEEDING_MAX = 40

    // ---- 주행 판정 ----
    /** 이보다 짧은 주행은 기록하지 않는다. (P↔D를 오가며 생기는 빈 주행 방지) */
    const val MIN_TRIP_KM = 0.05
    /** 기어 신호가 없을 때 이 시간(초) 이상 정지하면 정차 모드로 본다. */
    const val UNKNOWN_GEAR_PARK_AFTER_SEC = 10

    // ---- 포인트 사용 ----
    /** 정비비 차감 시 1포인트의 금액(원) */
    const val WON_PER_POINT = 1
}
