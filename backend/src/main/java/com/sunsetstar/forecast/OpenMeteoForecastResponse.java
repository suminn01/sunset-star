package com.sunsetstar.forecast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Open-Meteo 날씨 예보 API(/v1/forecast)의 응답을 담는 DTO.
 *
 * Open-Meteo는 시간별 값을 "시간 목록 + 값 목록"으로 나란히 보내 준다.
 * 같은 순번(index)끼리 짝이다. 예: hourly.time().get(18)이 18시라면
 * hourly.cloudCoverLow().get(18)이 그 시각의 낮은 구름양이다.
 *
 * 값이 비어 오는 시간도 있어서(null) 숫자는 int가 아닌 Integer·Double로 받는다.
 *
 * @param latitude  실제로 계산에 쓰인 위도 (요청한 값과 조금 다를 수 있음)
 * @param longitude 실제로 계산에 쓰인 경도
 * @param elevation 그 지점의 고도(m). F5 근처 장소의 "높은 곳" 보정에 쓴다
 * @param hourly    시간별 예보
 * @param daily     날짜별 예보 (일출·일몰)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenMeteoForecastResponse(
        Double latitude,
        Double longitude,
        Double elevation,
        Hourly hourly,
        Daily daily
) {

    /**
     * 시간별 예보. 7일 × 24시간 = 168개씩 들어온다.
     *
     * @param time          시각 (Asia/Seoul 기준)
     * @param cloudCover    전체 구름양(%) - 별 점수 ① 구름
     * @param cloudCoverLow 낮은 구름양(%) - 노을 점수 ② 낮은 구름
     * @param cloudCoverMid 중간 구름양(%) - 노을 점수 ① 위쪽 구름
     * @param cloudCoverHigh 높은 구름양(%) - 노을 점수 ① 위쪽 구름
     * @param humidity      상대 습도(%) - 노을 ⑤, 별 ② 습도
     * @param visibility    시정(m). 기능명세서 기준은 km라서 점수 계산 때 1000으로 나눈다
     * @param precipitation 강수량(mm) - 노을 ⑥ 비, 별 구름 상한 규칙
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hourly(
            List<LocalDateTime> time,
            @JsonProperty("cloud_cover") List<Integer> cloudCover,
            @JsonProperty("cloud_cover_low") List<Integer> cloudCoverLow,
            @JsonProperty("cloud_cover_mid") List<Integer> cloudCoverMid,
            @JsonProperty("cloud_cover_high") List<Integer> cloudCoverHigh,
            @JsonProperty("relative_humidity_2m") List<Integer> humidity,
            List<Double> visibility,
            List<Double> precipitation
    ) {
    }

    /**
     * 날짜별 예보. 7일치 7개씩 들어온다.
     *
     * @param time    날짜
     * @param sunrise 일출 시각
     * @param sunset  일몰 시각 - 노을 점수와 추천 시간의 기준
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Daily(
            List<LocalDate> time,
            List<LocalDateTime> sunrise,
            List<LocalDateTime> sunset
    ) {
    }
}
