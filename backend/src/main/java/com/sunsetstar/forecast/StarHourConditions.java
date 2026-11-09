package com.sunsetstar.forecast;

import java.time.LocalDateTime;

/**
 * 별 점수 한 시간치를 계산하는 데 필요한 값들.
 *
 * 노을의 SunsetConditions처럼, Open-Meteo 응답과 SkyCalculator 결과에서 그 시각 값만 골라 담는다.
 * 테스트에서는 "맑음, 공기 좋음, 그믐"처럼 원하는 상황을 바로 만들어 점수만 확인할 수 있다.
 *
 * @param time          시각 (정각)
 * @param cloudCover    전체 구름양(%)
 * @param pm25          초미세먼지 PM2.5 (μg/m³)
 * @param humidity      습도(%)
 * @param visibilityKm  시정(km)
 * @param precipitation 강수량(mm)
 * @param moon          그 시각의 달 밝기와 높이
 */
public record StarHourConditions(
        LocalDateTime time,
        Integer cloudCover,
        Double pm25,
        Integer humidity,
        Double visibilityKm,
        Double precipitation,
        SkyCalculator.Moon moon
) {
}
