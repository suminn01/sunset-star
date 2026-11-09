package com.sunsetstar.forecast;

import java.util.List;

/**
 * 하루치 해 지는 쪽 하늘 검사 결과 (기능명세서 F3).
 *
 * @param azimuth     일몰 순간 해의 방향 (북쪽 0°, 서쪽 270°)
 * @param direction   방향 이름 (예: "서남서")
 * @param points      검사 지점 3곳 (25·50·100km)
 * @param maxLowCloud 세 지점 낮은 구름 중 가장 많은 값(%). 노을 점수 ③에 쓴다. 세 곳 모두 값이 없으면 null
 */
public record WestSky(
        double azimuth,
        String direction,
        List<Point> points,
        Integer maxLowCloud
) {

    /**
     * 검사 지점 한 곳.
     *
     * @param distanceKm 내 위치에서의 거리 (25, 50, 100)
     * @param coordinate 지점 좌표
     * @param lowCloud   일몰 시각의 낮은 구름양(%). 값이 없으면 null
     * @param status     맑음 / 조금 / 막힘. 값이 없으면 null
     */
    public record Point(
            int distanceKm,
            Coordinate coordinate,
            Integer lowCloud,
            WestSkyStatus status
    ) {
    }
}
