package com.sunsetstar.forecast;

import java.util.List;

/**
 * 점수표의 한 줄(구간)을 나타낸다.
 *
 * 예: 기능명세서 F2 "① 위쪽 구름 30~70% → +25점, 높은 구름이 적당해서 분홍빛이 예쁘게 번질 것 같아요"
 * → new ScoreBand(30, 70, 25, "높은 구름이 적당해서 분홍빛이 예쁘게 번질 것 같아요")
 *
 * 노을(F2)과 별(F4) 점수표가 모두 "값의 구간 → 점수 + 이유 문장" 모양이라 같이 쓴다.
 *
 * @param min    구간 시작 (포함)
 * @param max    구간 끝 (포함). "80% 이상"처럼 끝이 없으면 Integer.MAX_VALUE
 * @param points 이 구간에 들어오면 받는 점수
 * @param reason 화면에 보여 줄 이유 문장. 점수표에 "(없음)"인 구간은 null
 */
public record ScoreBand(int min, int max, int points, String reason) {

    /** value가 이 구간 안에 있는지 확인한다. */
    public boolean contains(int value) {
        return min <= value && value <= max;
    }

    /**
     * 구간 목록에서 value가 들어가는 구간을 찾는다.
     * 예: find(UPPER_CLOUD, 45) → 30~70% 구간
     *
     * @throws IllegalArgumentException 어느 구간에도 안 들어가는 값일 때 (예: 구름 −5%)
     */
    public static ScoreBand find(List<ScoreBand> bands, int value) {
        for (ScoreBand band : bands) {
            if (band.contains(value)) {
                return band;
            }
        }
        throw new IllegalArgumentException("점수표 구간에 맞는 값이 아닙니다: " + value);
    }
}
