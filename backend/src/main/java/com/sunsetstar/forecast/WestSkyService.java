package com.sunsetstar.forecast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.sunsetstar.forecast.SunsetScoreRules.WEST_SKY_DISTANCES_KM;

/**
 * 해 지는 쪽 하늘 검사 (기능명세서 F3, 도전).
 *
 * 해는 지평선 근처에서 빛을 보내므로, 머리 위가 맑아도 해 지는 쪽 먼 하늘이 낮은 구름으로 막히면 노을이 생기지 않는다.
 *
 * 순서 (날짜마다)
 * 1. 일몰 순간 해의 방향을 구한다 (SkyCalculator)
 * 2. 그 방향으로 25·50·100km 떨어진 지점 좌표를 구한다 (Coordinate)
 * 3. 7일 × 3지점 = 21곳의 낮은 구름을 Open-Meteo에 한 번에 묻는다 (다중 좌표 요청 1번)
 * 4. 각 지점의 일몰 시각 낮은 구름으로 상태(맑음/조금/막힘)와 최댓값을 정한다
 *
 * 해 지는 방향은 하루에 약 1°씩 바뀌어서, 날짜마다 다른 세 지점을 본다.
 * 이 검사가 실패해도 노을 점수는 낼 수 있으므로, 실패하면 빈 결과를 돌려주고 ③은 0점으로 둔다.
 */
@Service
public class WestSkyService {

    private static final Logger log = LoggerFactory.getLogger(WestSkyService.class);

    /** 지점 좌표는 소수점 넷째 자리(약 10m)까지만 쓴다. 요청 주소가 짧아지고 캐시 키가 안정된다 */
    private static final double POINT_SCALE = 10_000.0;

    private final OpenMeteoClient client;
    private final SkyCalculator sky;

    public WestSkyService(OpenMeteoClient client, SkyCalculator sky) {
        this.client = client;
        this.sky = sky;
    }

    /**
     * 날짜별 해 지는 쪽 하늘 검사.
     *
     * @param weather 내 위치의 날씨 응답 (날짜별 일몰 시각을 쓴다)
     * @return 날짜 → 검사 결과. 검사에 실패하면 빈 Map
     */
    public Map<LocalDate, WestSky> check(OpenMeteoForecastResponse weather, double latitude, double longitude) {
        Coordinate center = new Coordinate(latitude, longitude);
        OpenMeteoForecastResponse.Daily daily = weather.daily();

        // 1~2. 날짜마다 해 지는 방향과 지점 3곳
        List<LocalDate> dates = new ArrayList<>();
        List<LocalDateTime> sunsets = new ArrayList<>();
        List<Double> azimuths = new ArrayList<>();
        List<Coordinate> points = new ArrayList<>();
        for (int day = 0; day < daily.time().size(); day++) {
            LocalDateTime sunset = valueAt(daily.sunset(), day);
            if (sunset == null) {
                continue;
            }
            double azimuth = sky.sunAzimuth(sunset, latitude, longitude);
            dates.add(daily.time().get(day));
            sunsets.add(sunset);
            azimuths.add(azimuth);
            for (int distance : WEST_SKY_DISTANCES_KM) {
                points.add(round(center.destination(azimuth, distance)));
            }
        }
        if (points.isEmpty()) {
            return Map.of();
        }

        // 3. 모든 지점을 한 번에 묻는다
        List<OpenMeteoForecastResponse> responses;
        try {
            responses = client.getLowCloud(points);
        } catch (RestClientException e) {
            log.warn("서쪽 하늘 조회 실패, ③ 서쪽 하늘을 0점으로 둡니다: {}", e.getMessage());
            return Map.of();
        }
        if (responses == null || responses.size() != points.size()) {
            log.warn("서쪽 하늘 응답 개수가 맞지 않습니다: 요청 {}곳", points.size());
            return Map.of();
        }

        // 4. 날짜마다 세 지점의 일몰 시각 값
        Map<LocalDate, WestSky> result = new HashMap<>();
        int k = 0;
        for (int d = 0; d < dates.size(); d++) {
            LocalDateTime sunsetHour = SunsetScoreService.nearestHour(sunsets.get(d));
            List<WestSky.Point> dayPoints = new ArrayList<>();
            for (int distance : WEST_SKY_DISTANCES_KM) {
                Integer lowCloud = lowCloudAt(responses.get(k), sunsetHour);
                WestSkyStatus status = lowCloud == null ? null : WestSkyStatus.of(lowCloud);
                dayPoints.add(new WestSky.Point(distance, points.get(k), lowCloud, status));
                k++;
            }
            double azimuth = azimuths.get(d);
            result.put(dates.get(d), new WestSky(
                    azimuth, SkyCalculator.directionName(azimuth), List.copyOf(dayPoints), maxLowCloud(dayPoints)));
        }
        return result;
    }

    /** 한 지점 응답에서 그 시각의 낮은 구름 */
    private Integer lowCloudAt(OpenMeteoForecastResponse response, LocalDateTime time) {
        if (response == null || response.hourly() == null || response.hourly().time() == null) {
            return null;
        }
        return valueAt(response.hourly().cloudCoverLow(), response.hourly().time().indexOf(time));
    }

    /** 세 지점 중 낮은 구름이 가장 많은 값. 한 곳이라도 막히면 빛이 못 들어오므로 평균이 아니라 최댓값 */
    private Integer maxLowCloud(List<WestSky.Point> points) {
        return points.stream()
                .map(WestSky.Point::lowCloud)
                .filter(Objects::nonNull)
                .max(Integer::compare)
                .orElse(null);
    }

    private Coordinate round(Coordinate c) {
        return new Coordinate(
                Math.round(c.latitude() * POINT_SCALE) / POINT_SCALE,
                Math.round(c.longitude() * POINT_SCALE) / POINT_SCALE);
    }

    private <T> T valueAt(List<T> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }
}
