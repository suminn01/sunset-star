package com.sunsetstar.forecast;

/**
 * 빛 공해 단계 (기능명세서 F1, F4). 사용자가 설정 화면에서 고르고, 기본값은 교외다.
 *
 * 별 점수 = (구름×0.5 + 공기×0.2 + 달×0.3) × 계수
 * 최고점 제한(도심 50점 등) 대신 곱하기를 쓴다. 제한 방식은 도심에서 좋은 밤과 나쁜 밤이
 * 같은 점수가 돼서 구분되지 않기 때문이다.
 */
public enum LightPollution {

    CITY("도심", 0.75,
            "도심에서 이 정도면 아주 좋은 밤이에요. 밝은 별과 별자리를 찾아보세요"),
    SUBURB("교외", 0.9, null),
    RURAL("시골", 1.0,
            "불빛이 적어서 은하수도 기대해 볼 만해요");

    /** 도심 이유 문장은 점수가 이 값 이상일 때만 보여 준다. */
    public static final int CITY_GOOD_NIGHT_MIN_SCORE = 70;

    private final String label;
    private final double factor;
    private final String reason;

    LightPollution(String label, double factor, String reason) {
        this.label = label;
        this.factor = factor;
        this.reason = reason;
    }

    /** 화면에 보여 줄 이름 (예: "교외") */
    public String label() {
        return label;
    }

    /** 점수에 곱하는 계수 (도심 0.75, 교외 0.9, 시골 1.0) */
    public double factor() {
        return factor;
    }

    /** 빛 공해 이유 문장. 교외는 없음(null) */
    public String reason() {
        return reason;
    }
}
