package com.sunsetstar.forecast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Open-Meteo에 요청을 보내고, 받은 JSON을 DTO에 담아 돌려주는 클래스.
 *
 * 하는 일은 두 가지다.
 * - getForecast: 7일치 시간별 구름·습도·시정·강수 + 날짜별 일출·일몰
 * - getAirQuality: 7일치 시간별 PM2.5·PM10
 * - getLowCloud: 여러 지점의 7일치 시간별 낮은 구름 (해 지는 쪽 하늘 검사, 요청 1번)
 *
 * 주소와 모델명은 OpenMeteoProperties(application.yml)에서 꺼내 쓰고,
 * 이 클래스에는 "무엇을 달라고 할지"만 적는다.
 *
 * 같은 좌표의 응답은 1시간 동안 캐시에 보관한다(@Cacheable). 1시간 안에 같은 좌표를 다시 물으면
 * Open-Meteo에 요청을 보내지 않고 보관해 둔 응답을 돌려준다. 좌표는 부르는 쪽(ForecastService)에서
 * 소수점 둘째 자리로 반올림해서 넘긴다. 실패한 요청(예외)은 보관하지 않는다.
 *
 * 응답이 늦거나 실패하면 RestClient가 예외를 던진다.
 * 재시도와 에러 응답({code, message}) 처리는 4단계(be/forecast-api)에서 붙인다.
 */
@Component
public class OpenMeteoClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);

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
    @Cacheable("open-meteo-forecast")
    public OpenMeteoForecastResponse getForecast(double latitude, double longitude) {
        // 캐시에 없을 때만 여기까지 온다. 콘솔에 이 줄이 찍히면 진짜로 Open-Meteo를 부른 것이다.
        log.info("Open-Meteo 날씨 요청: {}, {}", latitude, longitude);
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
    @Cacheable("open-meteo-air-quality")
    public OpenMeteoAirQualityResponse getAirQuality(double latitude, double longitude) {
        log.info("Open-Meteo 미세먼지 요청: {}, {}", latitude, longitude);
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

    /**
     * 여러 지점의 낮은 구름을 한 번에 가져온다 (다중 좌표 요청, 기능명세서 F3).
     * 위도·경도를 쉼표로 이어 보내면 Open-Meteo가 지점마다 응답 하나씩, 보낸 순서대로 배열로 돌려준다.
     * 예: latitude=37.47,37.38,37.18&longitude=126.72,126.47,125.96 → [지점1, 지점2, 지점3]
     *
     * 지점이 1곳이면 배열이 아니라 응답 하나만 오므로, 항상 2곳 이상을 보낸다 (해 지는 쪽 검사는 하루 3곳).
     */
    @Cacheable("open-meteo-west-sky")
    public List<OpenMeteoForecastResponse> getLowCloud(List<Coordinate> points) {
        log.info("Open-Meteo 서쪽 하늘 요청: {}곳", points.size());
        String latitudes = points.stream()
                .map(point -> String.valueOf(point.latitude()))
                .collect(Collectors.joining(","));
        String longitudes = points.stream()
                .map(point -> String.valueOf(point.longitude()))
                .collect(Collectors.joining(","));

        return restClient.get()
                .uri(properties.forecastUrl(), uri -> uri
                        .queryParam("latitude", latitudes)
                        .queryParam("longitude", longitudes)
                        .queryParam("hourly", "cloud_cover_low")
                        .queryParam("models", properties.model())
                        .queryParam("timezone", TIMEZONE)
                        .queryParam("forecast_days", FORECAST_DAYS)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<OpenMeteoForecastResponse>>() {
                });
    }
}
