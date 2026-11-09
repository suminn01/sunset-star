package com.sunsetstar.forecast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 해 지는 쪽 하늘 검사(F3) 테스트.
 *
 * Open-Meteo 대신 FakeClient를 쓴다. FakeClient는 요청받은 지점 순서대로
 * 25km → 10%, 50km → 45%, 100km → 75% 낮은 구름을 돌려준다(맑음 / 조금 / 막힘).
 * 해 방향은 진짜 SkyCalculator로 계산한다(인터넷 필요 없음).
 */
class WestSkyServiceTest {

    private static final double SEOUL_LAT = 37.57;
    private static final double SEOUL_LON = 126.98;
    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);

    private static class FakeClient extends OpenMeteoClient {
        private final boolean fail;
        int calls = 0;
        List<Coordinate> requested;

        FakeClient(boolean fail) {
            super(new OpenMeteoProperties("http://localhost", "http://localhost", "best_match"));
            this.fail = fail;
        }

        @Override
        public List<OpenMeteoForecastResponse> getLowCloud(List<Coordinate> points) {
            calls++;
            requested = points;
            if (fail) {
                throw new RestClientException("Open-Meteo 연결 실패 (테스트)");
            }
            int[] lowCloudByDistance = {10, 45, 75};
            List<OpenMeteoForecastResponse> responses = new ArrayList<>();
            for (int i = 0; i < points.size(); i++) {
                responses.add(pointWeather(lowCloudByDistance[i % 3]));
            }
            return responses;
        }

        private OpenMeteoForecastResponse pointWeather(int lowCloud) {
            List<LocalDateTime> time = new ArrayList<>();
            for (int h = 0; h < 48; h++) {
                time.add(DAY.atStartOfDay().plusHours(h));
            }
            OpenMeteoForecastResponse.Hourly hourly = new OpenMeteoForecastResponse.Hourly(
                    time, null, new ArrayList<>(Collections.nCopies(48, lowCloud)), null, null, null, null, null);
            return new OpenMeteoForecastResponse(0.0, 0.0, 0.0, hourly, null);
        }
    }

    /** 내 위치 날씨: 이틀, 일몰 17:26 / 17:25 */
    private OpenMeteoForecastResponse myWeather() {
        OpenMeteoForecastResponse.Daily daily = new OpenMeteoForecastResponse.Daily(
                List.of(DAY, DAY.plusDays(1)), null,
                List.of(DAY.atTime(17, 26), DAY.plusDays(1).atTime(17, 25)));
        return new OpenMeteoForecastResponse(SEOUL_LAT, SEOUL_LON, 38.0, null, daily);
    }

    @Test
    @DisplayName("이틀 × 3지점 = 6곳을 요청 1번으로 묻는다")
    void oneRequestForAllPoints() {
        FakeClient client = new FakeClient(false);

        new WestSkyService(client, new SkyCalculator()).check(myWeather(), SEOUL_LAT, SEOUL_LON);

        assertThat(client.calls).isEqualTo(1);
        assertThat(client.requested).hasSize(6);
    }

    @Test
    @DisplayName("11월 서울은 서남서 쪽으로 25·50·100km 지점을 본다")
    void pointsTowardSunset() {
        FakeClient client = new FakeClient(false);

        WestSky first = new WestSkyService(client, new SkyCalculator())
                .check(myWeather(), SEOUL_LAT, SEOUL_LON).get(DAY);

        assertThat(first.direction()).isEqualTo("서남서");
        assertThat(first.points()).extracting(WestSky.Point::distanceKm).containsExactly(25, 50, 100);
        // 서남서로 갈수록 경도가 줄어든다 (서쪽으로 멀어진다)
        assertThat(first.points().get(0).coordinate().longitude())
                .isGreaterThan(first.points().get(1).coordinate().longitude())
                .isLessThan(SEOUL_LON);
        assertThat(first.points().get(2).coordinate().longitude())
                .isLessThan(first.points().get(1).coordinate().longitude());
    }

    @Test
    @DisplayName("지점별 상태(맑음/조금/막힘)와, 점수에 쓸 최댓값(75)을 정한다")
    void statusAndMax() {
        FakeClient client = new FakeClient(false);

        WestSky first = new WestSkyService(client, new SkyCalculator())
                .check(myWeather(), SEOUL_LAT, SEOUL_LON).get(DAY);

        assertThat(first.points()).extracting(WestSky.Point::status)
                .containsExactly(WestSkyStatus.CLEAR, WestSkyStatus.PARTLY, WestSkyStatus.BLOCKED);
        assertThat(first.maxLowCloud()).isEqualTo(75);
    }

    @Test
    @DisplayName("Open-Meteo가 실패하면 빈 결과 (노을 ③은 0점으로 둔다)")
    void failure() {
        FakeClient client = new FakeClient(true);

        Map<LocalDate, WestSky> result = new WestSkyService(client, new SkyCalculator())
                .check(myWeather(), SEOUL_LAT, SEOUL_LON);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("상태 기준: 29 맑음, 30 조금, 60 조금, 61 막힘")
    void statusBoundaries() {
        assertThat(WestSkyStatus.of(29)).isEqualTo(WestSkyStatus.CLEAR);
        assertThat(WestSkyStatus.of(30)).isEqualTo(WestSkyStatus.PARTLY);
        assertThat(WestSkyStatus.of(60)).isEqualTo(WestSkyStatus.PARTLY);
        assertThat(WestSkyStatus.of(61)).isEqualTo(WestSkyStatus.BLOCKED);
    }
}
