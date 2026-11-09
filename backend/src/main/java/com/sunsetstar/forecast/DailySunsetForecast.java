package com.sunsetstar.forecast;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 하루치 노을 예보. 7일 예보 카드(F6) 한 장의 노을 부분이 된다.
 *
 * @param date          날짜
 * @param sunset        일몰 시각
 * @param score         노을 점수, 등급, 요소 목록
 * @param recommendFrom 추천 시작 시각 (일몰 15분 전). 쉬는 날이면 null
 * @param recommendTo   추천 끝 시각 (일몰 20분 후). 쉬는 날이면 null
 * @param note          함께 보여 줄 안내. 추천 시간이 있으면 "해가 진 뒤에도 바로 떠나지 마세요",
 *                      30점 미만이면 "오늘은 쉬어요"
 */
public record DailySunsetForecast(
        LocalDate date,
        LocalDateTime sunset,
        SunsetScore score,
        LocalDateTime recommendFrom,
        LocalDateTime recommendTo,
        String note
) {

    /** 30점 미만이라 추천 시간 대신 쉬는 날 안내를 보여 주는 날인지 */
    public boolean isRestDay() {
        return recommendFrom == null;
    }
}
