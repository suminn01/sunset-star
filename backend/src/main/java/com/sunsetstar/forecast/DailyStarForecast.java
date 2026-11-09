package com.sunsetstar.forecast;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 하룻밤의 별 예보. 7일 예보 카드(F6) 한 장의 별 부분이 된다.
 *
 * @param date          날짜 (그날 저녁부터 다음 날 새벽까지가 그날 밤이다)
 * @param nightStart    천문박명이 끝나는 시각
 * @param hours         시간별 점수 (정각마다)
 * @param score         하루 대표 점수 = 시간별 점수 중 가장 높은 값
 * @param grade         대표 점수의 등급
 * @param recommendFrom 추천 구간 시작. 쉬는 날이면 null
 * @param recommendTo   추천 구간 끝. 쉬는 날이면 null
 * @param note          쉬는 날이면 "오늘 밤은 쉬어 가는 날이에요", 아니면 null
 * @param moonNote      초저녁에 떠 있던 달이 밤 중간에 지면 "달이 01:10에 지면 더 좋아요", 아니면 null
 * @param lightNote     빛 공해 이유 문장 (도심은 70점 이상일 때만, 교외는 없음)
 */
public record DailyStarForecast(
        LocalDate date,
        LocalDateTime nightStart,
        List<HourlyStarScore> hours,
        int score,
        Grade grade,
        LocalDateTime recommendFrom,
        LocalDateTime recommendTo,
        String note,
        String moonNote,
        String lightNote
) {

    /** 대표 점수가 30점 미만이라 추천 구간 대신 쉬는 날 안내를 보여 주는 날인지 */
    public boolean isRestDay() {
        return recommendFrom == null;
    }
}
