package com.drivecash.app.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class PlaceKind(val label: String) {
    GAS("주유소"),
    EV("충전소"),
    SERVICE("정비소"),
}

/**
 * 포인트를 쓸 수 있는 장소.
 *
 * **시연용 예시 데이터다.** 실제 영업점의 이름·주소·좌표가 아니다.
 * 실제 서비스에서는 내비게이션 POI 검색이나 제휴사 데이터로 바꾼다. (docs/DEVELOPMENT.md V-10)
 */
data class Place(
    val name: String,
    val kind: PlaceKind,
    val latitude: Double,
    val longitude: Double,
)

data class NearbyPlace(val place: Place, val distanceMeters: Double)

object Places {

    /** 시연용 기준 좌표. Pleos 에뮬레이터의 기본 위치(37.422, -122.084) 부근에서 거리가 눈에 보이게 한다. */
    private const val BASE_LAT = 37.4220
    private const val BASE_LON = -122.0840

    /** 위도 0.001도는 약 111m 이다. */
    val demo: List<Place> = listOf(
        Place("시연 A", PlaceKind.GAS, BASE_LAT + 0.0030, BASE_LON + 0.0020),
        Place("시연 B", PlaceKind.GAS, BASE_LAT - 0.0100, BASE_LON + 0.0150),
        Place("시연 A", PlaceKind.EV, BASE_LAT + 0.0060, BASE_LON - 0.0040),
        Place("시연 B", PlaceKind.EV, BASE_LAT - 0.0200, BASE_LON - 0.0200),
        Place("시연 A", PlaceKind.SERVICE, BASE_LAT - 0.0040, BASE_LON - 0.0060),
        Place("시연 B", PlaceKind.SERVICE, BASE_LAT + 0.0300, BASE_LON + 0.0300),
    )

    /** 종류별로 가장 가까운 장소 1곳씩, 가까운 순서로 돌려준다. */
    fun nearestPerKind(latitude: Double, longitude: Double, places: List<Place> = demo): List<NearbyPlace> =
        places
            .map { NearbyPlace(it, distanceMeters(latitude, longitude, it.latitude, it.longitude)) }
            .groupBy { it.place.kind }
            .mapNotNull { (_, list) -> list.minByOrNull { it.distanceMeters } }
            .sortedBy { it.distanceMeters }

    /** 두 지점 사이의 대원 거리(미터). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun formatDistance(meters: Double): String =
        if (meters < 1000) "${meters.toInt()}m" else "%.1fkm".format(meters / 1000)
}
