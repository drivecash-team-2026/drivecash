package com.drivecash.app.data.signals

import com.drivecash.app.domain.Sample

/** 스팟 구간. 근접 알림(FusedLocation)으로 진입을 감지한다. */
data class BonusZone(val name: String, val latitude: Double, val longitude: Double, val radiusMeters: Float)

/** 차량에서 값을 읽어오는 계층. 실제 구현은 [PleosVehicleSignals]. */
interface VehicleSignals {
    /** 신호 수집 시작. 여러 번 호출해도 한 번만 시작한다. */
    fun start()

    fun stop()

    /** 지금 시점의 값을 모아 돌려준다. 1초마다 호출된다. */
    fun snapshot(nowMs: Long): Sample

    /** 스팟 구간 진입 콜백 등록 */
    fun setZoneListener(listener: (zoneName: String) -> Unit)

    /** 마지막으로 받은 현재 위치(위도, 경도). 아직 받지 못했으면 null */
    fun currentLocation(): Pair<Double, Double>?

    /** 연결 상태를 사람이 읽을 수 있게 요약한 문자열 (개발자 화면용) */
    fun statusSummary(): String
}
