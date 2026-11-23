package com.sunsetstar.observation;

import java.util.List;

/**
 * 내 관측 통계 (기능명세서 F7, 기획서의 성공 지표).
 *
 * | 칸                      | 계산                                                     |
 * | totalCount              | 내 기록 전체 수                                            |
 * | hitRate                 | 적중 수 ÷ 판정한 기록 수 × 100 (SOSO는 빼고 셈), 반올림한 %    |
 * | recommendationSatisfaction | 추천 장소에서 본 기록 중 GOOD(예뻤어요) 비율 %             |
 * | notificationCount       | 알림을 눌러 들어와 남긴 기록 수                              |
 *
 * 비율을 낼 기록이 하나도 없으면 0%가 아니라 null이다. "0%"는 "다 틀렸다"로 읽혀서, 화면에서는 "아직 기록이 없어요"로 보여 준다.
 *
 * @param judgedCount 적중률 계산에 들어간 기록 수 (GOOD + BAD). 화면 문구 "(기록 n건)"에 쓴다
 */
public record ObservationStatsResponse(
        int totalCount,
        Integer hitRate,
        int hitCount,
        int judgedCount,
        Integer recommendationSatisfaction,
        int recommendationCount,
        int notificationCount
) {

    public static ObservationStatsResponse from(List<Observation> observations) {
        int judged = 0;
        int hits = 0;
        int recommended = 0;
        int recommendedGood = 0;
        int fromNotification = 0;

        for (Observation observation : observations) {
            Boolean hit = observation.isHit();
            if (hit != null) {
                judged++;
                if (hit) {
                    hits++;
                }
            }
            if (observation.isFromRecommendation()) {
                recommended++;
                if (observation.getRating() == Rating.GOOD) {
                    recommendedGood++;
                }
            }
            if (observation.isFromNotification()) {
                fromNotification++;
            }
        }

        return new ObservationStatsResponse(observations.size(),
                percent(hits, judged), hits, judged,
                percent(recommendedGood, recommended), recommended,
                fromNotification);
    }

    /** part ÷ whole × 100 을 반올림. whole이 0이면 null */
    static Integer percent(int part, int whole) {
        return whole == 0 ? null : (int) Math.round(part * 100.0 / whole);
    }
}
