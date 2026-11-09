package com.sunsetstar.forecast;

import java.util.List;

/**
 * 노을 점수 계산 결과.
 *
 * @param score   최종 점수 (0~100)
 * @param grade   등급 (꼭 보세요 ~ 쉬어 가요)
 * @param factors 요소별 가감점 목록. ①위쪽 구름 → ⑥비 순서
 * @param missing 값이 없어서 빼고 계산한 요소 이름 (예: ["공기"]). 화면에 "일부 정보 없음"을 띄울 때 쓴다
 */
public record SunsetScore(
        int score,
        Grade grade,
        List<Factor> factors,
        List<String> missing
) {
}
