package com.sunsetstar.forecast;

/**
 * 해 지는 쪽 검사 지점 한 곳의 상태 (기능명세서 F3 "지점별 상태 표시").
 * 상세 화면에서 25·50·100km 지점마다 "맑음 / 조금 / 막힘"으로 보여 준다.
 *
 * | 낮은 구름 | 상태 |
 * | 30% 미만  | 맑음 |
 * | 30~60%    | 조금 |
 * | 60% 초과  | 막힘 |
 */
public enum WestSkyStatus {

    CLEAR("맑음"),
    PARTLY("조금"),
    BLOCKED("막힘");

    /** 이 값(%) 미만이면 맑음 */
    static final int CLEAR_BELOW = 30;
    /** 이 값(%) 초과면 막힘 */
    static final int BLOCKED_ABOVE = 60;

    private final String label;

    WestSkyStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 낮은 구름양(%)으로 상태를 정한다. 예: 25 → 맑음, 45 → 조금, 75 → 막힘 */
    public static WestSkyStatus of(int lowCloud) {
        if (lowCloud < CLEAR_BELOW) {
            return CLEAR;
        }
        if (lowCloud > BLOCKED_ABOVE) {
            return BLOCKED;
        }
        return PARTLY;
    }
}
