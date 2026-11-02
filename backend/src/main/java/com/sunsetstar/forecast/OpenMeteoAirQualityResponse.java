package com.sunsetstar.forecast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Open-Meteo 대기질 API(/v1/air-quality)의 응답을 담는 DTO.
 *
 * 날씨 API와 주소가 달라서 따로 요청하고, 응답도 따로 받는다.
 * 시간 목록과 값 목록을 같은 순번끼리 짝지어 읽는 방식은 날씨 API와 같다.
 *
 * @param latitude  실제로 계산에 쓰인 위도
 * @param longitude 실제로 계산에 쓰인 경도
 * @param hourly    시간별 미세먼지
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenMeteoAirQualityResponse(
        Double latitude,
        Double longitude,
        Hourly hourly
) {

    /**
     * 시간별 미세먼지 (단위 μg/m³).
     *
     * @param time 시각 (Asia/Seoul 기준)
     * @param pm25 초미세먼지 PM2.5 - 노을 ④, 별 ② 공기 점수에 쓴다
     * @param pm10 미세먼지 PM10 - 지금은 점수에 안 쓰지만 화면 표시용으로 받아 둔다
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hourly(
            List<LocalDateTime> time,
            @JsonProperty("pm2_5") List<Double> pm25,
            @JsonProperty("pm10") List<Double> pm10
    ) {
    }
}
