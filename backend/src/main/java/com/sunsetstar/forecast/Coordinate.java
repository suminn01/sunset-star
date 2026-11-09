package com.sunsetstar.forecast;

/**
 * 지구 위의 한 지점 (위도, 경도).
 *
 * destination으로 "여기서 어느 방향으로 몇 km 간 지점"을 구할 수 있다.
 * 해 지는 쪽 하늘 검사(기능명세서 F3)에서 일몰 방향으로 25·50·100km 떨어진 지점을 구할 때 쓴다.
 *
 * @param latitude  위도 (북쪽이 +)
 * @param longitude 경도 (동쪽이 +)
 */
public record Coordinate(double latitude, double longitude) {

    /** 지구 평균 반지름 (km) */
    static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * 이 지점에서 bearing 방향으로 distanceKm 떨어진 지점.
     * 지구가 둥글다는 것을 반영한 공식(대권 항법)을 쓴다.
     * 예: 서울(37.5665, 126.978)에서 서쪽(270°)으로 100km → (37.561, 125.844)
     *
     * @param bearingDegrees 방향 (북쪽 0°, 동쪽 90°, 남쪽 180°, 서쪽 270°)
     * @param distanceKm     거리 (km)
     */
    public Coordinate destination(double bearingDegrees, double distanceKm) {
        double angularDistance = distanceKm / EARTH_RADIUS_KM; // 지구 중심에서 본 각도
        double bearing = Math.toRadians(bearingDegrees);
        double lat1 = Math.toRadians(latitude);
        double lon1 = Math.toRadians(longitude);

        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(angularDistance)
                + Math.cos(lat1) * Math.sin(angularDistance) * Math.cos(bearing));
        double lon2 = lon1 + Math.atan2(
                Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(lat1),
                Math.cos(angularDistance) - Math.sin(lat1) * Math.sin(lat2));

        return new Coordinate(Math.toDegrees(lat2), Math.toDegrees(lon2));
    }
}
