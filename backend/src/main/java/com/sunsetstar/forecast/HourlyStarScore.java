package com.sunsetstar.forecast;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 한 시간의 별 점수. 상세 화면의 시간대별 막대그래프 한 칸이 된다.
 *
 * 노을과 달리 factors의 점수는 "더하거나 뺀 값"이 아니라 요소별 0~100점이다.
 * 예: 구름 80, 공기 75, 달 100 → (80×0.5 + 75×0.2 + 100×0.3) × 계수
 *
 * @param time    시각 (정각)
 * @param score   최종 점수 (0~100)
 * @param factors 구름, 공기, 달 요소 점수와 이유 문장
 * @param missing 값이 없어서 빼고 계산한 요소 이름
 */
public record HourlyStarScore(
        LocalDateTime time,
        int score,
        List<Factor> factors,
        List<String> missing
) {
}
