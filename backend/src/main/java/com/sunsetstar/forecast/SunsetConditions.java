package com.sunsetstar.forecast;

/**
 * 노을 점수를 계산하는 데 필요한 값들. 모두 "그날 일몰 시각"의 예보 값이다.
 *
 * Open-Meteo 응답(168시간치)에서 일몰 시각의 값만 골라 이 그릇에 담은 뒤 SunsetScoreService에 넘긴다.
 * 값을 고르는 일과 점수를 매기는 일을 나눠 두면, 테스트에서 "높은 구름 45%, 낮은 구름 25%"처럼
 * 원하는 상황을 바로 만들어 점수만 확인할 수 있다.
 *
 * 값이 없을 수 있어서(null) 모두 Integer·Double로 둔다. 없는 값의 요소는 빼고 계산한다(기능명세서 F8).
 *
 * @param cloudCoverHigh     높은 구름(%)
 * @param cloudCoverMid      중간 구름(%)
 * @param cloudCoverLow      낮은 구름(%) - 내 위치
 * @param westSkyLowCloud    해 지는 쪽 세 지점 낮은 구름 중 최댓값(%). 5단계(F3) 전까지는 null
 * @param pm25               초미세먼지 PM2.5 (μg/m³)
 * @param humidity           습도(%)
 * @param visibilityKm       시정(km). Open-Meteo는 m로 주므로 1000으로 나눠서 넣는다
 * @param rainAtSunsetMm     일몰 시각 강수량(mm)
 * @param rainBeforeSunsetMm 일몰 전 6시간 강수량 합(mm)
 */
public record SunsetConditions(
        Integer cloudCoverHigh,
        Integer cloudCoverMid,
        Integer cloudCoverLow,
        Integer westSkyLowCloud,
        Double pm25,
        Integer humidity,
        Double visibilityKm,
        Double rainAtSunsetMm,
        Double rainBeforeSunsetMm
) {
}
