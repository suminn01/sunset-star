package com.sunsetstar.forecast;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Open-Meteo에 요청을 보내고, 받은 JSON을 DTO에 담아 돌려주는 클래스.
 *
 * 하는 일은 두 가지다.
 * - getForecast: 7일치 시간별 구름·습도·시정·강수 + 날짜별 일출·일몰
 * - getAirQuality: 7일치 시간별 PM2.5·PM10
 *
 * 주소와 모델명은 OpenMeteoProperties(application.yml)에서 꺼내 쓰고,
 * 이 클래스에는 "무엇을 달라고 할지"만 적는다.
 *
 * 응답이 늦거나 실패하면 RestClient가 예외를 던진다.
 * 재시도와 에러 응답({code, message}) 처리는 4단계(be/forecast-api)에서 붙인다.
 */
@Component
public class OpenMeteoClient {

    /** 모든 시각을 한국 시간으로 받는다. */
    private static final String TIMEZONE = "Asia/Seoul";

    /** 오늘부터 7일치 (기능명세서 F2·F4·F6). */
    private static final int FORECAST_DAYS = 7;

    /** 날씨 API에 달라고 할 시간별 항목. 이름은 Open-Meteo 문서에 있는 그대로 쓴다. */
    private static final String HOURLY_WEATHER = String.join(",",
            "cloud_cover",          // 전체 구름
            "cloud_cover_low",      // 낮은 구름
            "cloud_cover_mid",      // 중간 구름
            "cloud_cover_high",     // 높은 구름
            "relative_humidity_2m", // 습도
            "visibility",           // 시정
            "precipitation"         // 강수량
    );

    /** 날씨 API에 달라고 할 날짜별 항목. */
    private static final String DAILY_WEATHER = "sunrise,sunset";

    /** 대기질 API에 달라고 할 시간별 항목. */
    private static final String HOURLY_AIR_QUALITY = "pm2_5,pm10";

    /** 연결이 안 되면 5초, 응답이 안 오면 10초 뒤에 포기한다. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final OpenMeteoProperties properties;
    private final RestClient restClient;

    public OpenMeteoClient(OpenMeteoProperties properties) {
        this.properties = properties;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 7일치 날씨 예보를 가져온다.
     * 실제 요청 주소 예:
     * https://api.open-meteo.com/v1/forecast?latitude=37.5665&longitude=126.978&hourly=cloud_cover,...
     */
    public OpenMeteoForecastResponse getForecast(double latitude, double longitude) {
        return restClient.get()
                .uri(properties.forecastUrl(), uri -> uri
                        .queryParam("latitude", latitude)
                        .queryParam("longitude", longitude)
                        .queryParam("hourly", HOURLY_WEATHER)
                        .queryParam("daily", DAILY_WEATHER)
                        .queryParam("models", properties.model())
                        .queryParam("timezone", TIMEZONE)
                        .queryParam("forecast_days", FORECAST_DAYS)
                        .build())
                .retrieve()
                .body(OpenMeteoForecastResponse.class);
    }

    /**
     * 7일치 미세먼지 예보를 가져온다.
     * 날씨와 주소가 다른 API라서 요청을 따로 보낸다.
     */
    public OpenMeteoAirQualityResponse getAirQuality(double latitude, double longitude) {
        return restClient.get()
                .uri(properties.airQualityUrl(), uri -> uri
                        .queryParam("latitude", latitude)
                        .queryParam("longitude", longitude)
                        .queryParam("hourly", HOURLY_AIR_QUALITY)
                        .queryParam("timezone", TIMEZONE)
                        .queryParam("forecast_days", FORECAST_DAYS)
                        .build())
                .retrieve()
                .body(OpenMeteoAirQualityResponse.class);
    }
}
