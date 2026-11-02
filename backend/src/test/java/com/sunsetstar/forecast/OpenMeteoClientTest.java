package com.sunsetstar.forecast;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenMeteoClient가 실제 Open-Meteo에서 7일치 값을 받아오는지 확인하는 테스트.
 *
 * 진짜 인터넷 요청을 보내므로 인터넷이 끊겨 있으면 실패한다.
 *
 * 서버 전체(Spring)를 띄우지 않고 OpenMeteoClient만 직접 만들어서 쓴다.
 * 서버 전체를 띄우면 MySQL까지 켜져 있어야 해서, 날씨 요청과 상관없는 이유로 실패할 수 있기 때문이다.
 * 그래서 설정값도 application.yml과 같은 값을 여기에 직접 넣었다.
 */
class OpenMeteoClientTest {

    // 서울시청 좌표
    private static final double SEOUL_LAT = 37.5665;
    private static final double SEOUL_LON = 126.9780;

    private static final int DAYS = 7;
    private static final int HOURS = DAYS * 24; // 168

    private OpenMeteoClient client;

    @BeforeEach
    void setUp() {
        OpenMeteoProperties properties = new OpenMeteoProperties(
                "https://api.open-meteo.com/v1/forecast",
                "https://air-quality-api.open-meteo.com/v1/air-quality",
                "best_match"
        );
        client = new OpenMeteoClient(properties);
    }

    @Test
    @DisplayName("서울 날씨: 시간별 값 168개, 일몰 7개가 들어온다")
    void getForecast_seoul() {
        OpenMeteoForecastResponse response = client.getForecast(SEOUL_LAT, SEOUL_LON);

        // 시간별: 7일 × 24시간
        OpenMeteoForecastResponse.Hourly hourly = response.hourly();
        assertThat(hourly.time()).hasSize(HOURS);
        assertThat(hourly.cloudCover()).hasSize(HOURS);
        assertThat(hourly.cloudCoverLow()).hasSize(HOURS);
        assertThat(hourly.cloudCoverMid()).hasSize(HOURS);
        assertThat(hourly.cloudCoverHigh()).hasSize(HOURS);
        assertThat(hourly.humidity()).hasSize(HOURS);
        assertThat(hourly.visibility()).hasSize(HOURS);
        assertThat(hourly.precipitation()).hasSize(HOURS);

        // 구름양은 0~100% 사이여야 한다 (비어 있는 값은 빼고 확인)
        assertThat(hourly.cloudCoverLow())
                .filteredOn(Objects::nonNull)
                .isNotEmpty()
                .allSatisfy(value -> assertThat(value).isBetween(0, 100));

        // 날짜별: 7일, 첫날은 한국 기준 오늘
        OpenMeteoForecastResponse.Daily daily = response.daily();
        assertThat(daily.time()).hasSize(DAYS);
        assertThat(daily.time().getFirst()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")));
        assertThat(daily.sunset()).hasSize(DAYS).doesNotContainNull();

        // 서울 일몰은 계절에 따라 17시대(겨울)~19시대(여름)
        assertThat(daily.sunset().getFirst().getHour()).isBetween(17, 19);
    }

    @Test
    @DisplayName("서울 미세먼지: 시간별 PM2.5 168개가 들어온다")
    void getAirQuality_seoul() {
        OpenMeteoAirQualityResponse response = client.getAirQuality(SEOUL_LAT, SEOUL_LON);

        OpenMeteoAirQualityResponse.Hourly hourly = response.hourly();
        assertThat(hourly.time()).hasSize(HOURS);
        assertThat(hourly.pm25()).hasSize(HOURS);
        assertThat(hourly.pm10()).hasSize(HOURS);

        // PM2.5는 0 이상이어야 한다
        assertThat(hourly.pm25())
                .filteredOn(Objects::nonNull)
                .isNotEmpty()
                .allSatisfy(value -> assertThat(value).isGreaterThanOrEqualTo(0.0));
    }
}
