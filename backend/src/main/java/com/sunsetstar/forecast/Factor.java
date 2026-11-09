package com.sunsetstar.forecast;

/**
 * 점수에 영향을 준 요소 하나. 기능명세서의 {요소, 점수, 설명}이다.
 *
 * 예: new Factor("위쪽 구름", 25, "높은 구름이 적당해서 분홍빛이 예쁘게 번질 것 같아요")
 *
 * 노을 점수를 계산하면 요소마다 Factor가 하나씩 만들어지고, 점수와 함께 목록으로 돌려준다.
 * - F6 메인 카드: 점수의 절댓값이 가장 큰 Factor의 이유 문장 1개를 보여 준다
 * - F6 상세 화면: 0점이 아닌 Factor를 모두 "문장 + 점수"로 보여 준다
 * - F7 관측 기록: 기록하는 순간의 Factor 목록을 함께 저장해 나중에 비교한다
 *
 * @param name   요소 이름 (예: "위쪽 구름", "공기")
 * @param points 더하거나 뺀 점수 (예: 25, -10)
 * @param reason 화면에 보여 줄 이유 문장. 0점이라 문장이 없으면 null
 */
public record Factor(String name, int points, String reason) {

    /**
     * 점수표의 한 줄(ScoreBand)로 Factor를 만든다.
     * 예: Factor.from("위쪽 구름", ScoreBand.find(SunsetScoreRules.UPPER_CLOUD, 45))
     */
    public static Factor from(String name, ScoreBand band) {
        return new Factor(name, band.points(), band.reason());
    }
}
