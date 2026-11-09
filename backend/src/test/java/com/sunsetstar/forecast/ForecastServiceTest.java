package com.sunsetstar.forecast;

import com.sunsetstar.global.ApiException;
import com.sunsetstar.global.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ForecastService 테스트: 날씨 실패 시 재시도와 503, 좌표 반올림, 범위 밖 날짜.
 *
 * Open-Meteo 대신 FakeClient를 끼워서, 몇 번째 요청에 실패할지 정할 수 있다. 인터넷이 필요 없다.
 * (미세먼지와 서쪽 하늘 요청은 항상 실패시켜서, 날씨만으로 점수가 나오는지도 함께 본다)
 */
class ForecastServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);

    /**
     * 가짜 OpenMeteoClient. failTimes번까지는 실패하고, 그다음부터는 이틀치 맑은 날씨를 돌려준다.
     * 미세먼지는 항상 실패한다(공기를 빼고 계산되는지도 함께 확인).
     */
    private static class FakeClient extends OpenMeteoClient {
        private final int failTimes;
        int calls = 0;
        double lastLatitude;
        double lastLongitude;

        FakeClient(int failTimes) {
            super(new OpenMeteoProperties("http://localhost", "http://localhost", "best_match"));
            this.failTimes = failTimes;
        }

        @Override
        public OpenMeteoForecastResponse getForecast(double latitude, double longitude) {
            calls++;
            lastLatitude = latitude;
            lastLongitude = longitude;
            if (calls <= failTimes) {
                throw new RestClientException("Open-Meteo 연결 실패 (테스트)");
            }
            return clearWeather();
        }

        @Override
        public OpenMeteoAirQualityResponse getAirQuality(double latitude, double longitude) {
            throw new RestClientException("대기질 연결 실패 (테스트)");
        }

        @Override
        public List<OpenMeteoForecastResponse> getLowCloud(List<Coordinate> points) {
            throw new RestClientException("서쪽 하늘 연결 실패 (테스트)");
        }

        private OpenMeteoForecastResponse clearWeather() {
            List<LocalDateTime> time = new ArrayList<>();
            for (int h = 0; h < 48; h++) {
                time.add(DAY.atStartOfDay().plusHours(h));
            }
            OpenMeteoForecastResponse.Hourly hourly = new OpenMeteoForecastResponse.Hourly(
                    time, repeat(0), repeat(0), repeat(0), repeat(0), repeat(60), repeat(20000.0), repeat(0.0));
            OpenMeteoForecastResponse.Daily daily = new OpenMeteoForecastResponse.Daily(
                    List.of(DAY, DAY.plusDays(1)), null,
                    List.of(DAY.atTime(17, 27), DAY.plusDays(1).atTime(17, 26)));
            return new OpenMeteoForecastResponse(37.57, 126.98, 38.0, hourly, daily);
        }

        private <T> List<T> repeat(T value) {
            return new ArrayList<>(Collections.nCopies(48, value));
        }
    }

    private ForecastService serviceWith(FakeClient client) {
        SkyCalculator sky = new SkyCalculator();
        return new ForecastService(client, new SunsetScoreService(), new StarScoreService(sky), sky,
                new WestSkyService(client, sky));
    }

    @Test
    @DisplayName("날씨 요청이 한 번 실패해도 한 번 더 시도해서 예보를 만든다")
    void retryOnce() {
        FakeClient client = new FakeClient(1);

        ForecastResponse response = serviceWith(client).forecast(37.5665, 126.978, LightPollution.SUBURB);

        assertThat(client.calls).isEqualTo(2);
        assertThat(response.days()).hasSize(2);
        // 미세먼지는 실패했으므로 공기를 빼고 계산했다고 표시된다
        assertThat(response.days().getFirst().sunset().partial()).isTrue();
    }

    @Test
    @DisplayName("다시 시도해도 실패하면 503 WEATHER_UNAVAILABLE")
    void failTwice() {
        FakeClient client = new FakeClient(2);

        assertThatThrownBy(() -> serviceWith(client).forecast(37.5665, 126.978, LightPollution.SUBURB))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).errorCode()).isEqualTo(ErrorCode.WEATHER_UNAVAILABLE));
        assertThat(client.calls).isEqualTo(2); // 처음 1번 + 재시도 1번
    }

    @Test
    @DisplayName("좌표를 소수점 둘째 자리로 반올림해서 Open-Meteo에 묻는다 (캐시 키 통일)")
    void roundCoordinates() {
        FakeClient client = new FakeClient(0);

        ForecastResponse response = serviceWith(client).forecast(37.5665, 126.978, LightPollution.SUBURB);

        assertThat(client.lastLatitude).isEqualTo(37.57);
        assertThat(client.lastLongitude).isEqualTo(126.98);
        assertThat(response.latitude()).isEqualTo(37.57);
    }

    @Test
    @DisplayName("반올림: 37.5665 → 37.57, 126.978 → 126.98, −33.8688 → −33.87")
    void roundCoordinateValues() {
        assertThat(ForecastService.roundCoordinate(37.5665)).isEqualTo(37.57);
        assertThat(ForecastService.roundCoordinate(126.978)).isEqualTo(126.98);
        assertThat(ForecastService.roundCoordinate(-33.8688)).isEqualTo(-33.87);
    }

    @Test
    @DisplayName("예보에 없는 날짜로 상세를 물으면 400 INVALID_PARAMETER")
    void detailOutOfRange() {
        FakeClient client = new FakeClient(0);

        assertThatThrownBy(() -> serviceWith(client).detail(37.57, 126.98, LightPollution.SUBURB, DAY.plusDays(30)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).errorCode()).isEqualTo(ErrorCode.INVALID_PARAMETER));
    }
}
