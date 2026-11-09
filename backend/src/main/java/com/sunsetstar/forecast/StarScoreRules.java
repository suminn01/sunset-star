package com.sunsetstar.forecast;

import java.util.List;

/**
 * 별 점수(기능명세서 F4)의 기준 숫자와 이유 문장을 한곳에 모은 클래스.
 *
 * 밤을 1시간 단위로 나눠 시간마다 점수를 매긴다.
 * 시간별 점수 = (① 구름 × 0.5 + ② 공기 × 0.2 + ③ 달 × 0.3) × 빛 공해 계수, 소수점은 반올림
 *
 * ①②③은 각각 0~100점이고, 비중(0.5, 0.2, 0.3)을 곱해 더한다.
 * 노을 점수처럼 50에서 더하고 빼는 방식이 아니라는 점이 다르다.
 * 빛 공해 계수는 LightPollution에 있다. 숫자는 모두 기능명세서 Ver.1.5의 초기값이다.
 */
public final class StarScoreRules {

    private StarScoreRules() {
    }

    // ===== 비중 =====

    public static final double CLOUD_WEIGHT = 0.5;
    public static final double AIR_WEIGHT = 0.2;
    public static final double MOON_WEIGHT = 0.3;

    // ===== ① 구름 (전체 구름양, %) =====

    public static final List<ScoreBand> CLOUD = List.of(
            new ScoreBand(0, 10, 100, "구름이 거의 없어요"),
            new ScoreBand(11, 30, 80, "구름이 조금 있지만 사이사이로 잘 보여요"),
            new ScoreBand(31, 50, 50, "구름이 절반쯤 가릴 것 같아요"),
            new ScoreBand(51, 70, 25, "구름이 많아서 틈새로만 보여요"),
            new ScoreBand(71, 90, 10, "구름이 하늘 대부분을 가려요"),
            new ScoreBand(91, 100, 0, "구름이 하늘을 덮었어요")
    );

    // ===== ② 공기 (PM2.5, μg/m³, 반올림한 정수로 찾는다) =====

    public static final List<ScoreBand> AIR = List.of(
            new ScoreBand(0, 15, 100, "공기가 맑아서 별이 또렷해요"),
            new ScoreBand(16, 35, 75, null),
            new ScoreBand(36, 75, 40, "미세먼지 때문에 희미한 별은 안 보일 수 있어요"),
            new ScoreBand(76, Integer.MAX_VALUE, 10, "미세먼지가 심해서 별이 거의 안 보일 것 같아요")
    );

    /** 습도가 이 값(%) 이상이거나 시정이 HAZE_VISIBILITY_KM 미만이면 공기 점수에서 HAZE_PENALTY를 뺀다 (최저 0) */
    public static final int HAZE_HUMIDITY_MIN = 90;
    public static final double HAZE_VISIBILITY_KM = 5.0;
    public static final int HAZE_PENALTY = 30;
    public static final String HAZE_REASON = "습기 때문에 하늘이 뿌얘요";

    // ===== ③ 달 =====

    /** 달이 진 시간 → 100점 */
    public static final int MOON_DOWN_SCORE = 100;
    public static final String MOON_DOWN_REASON = "달이 진 뒤라 하늘이 어두워요";

    /** 달 밝기가 이 값 미만(그믐 무렵)이면 떠 있어도 100점 */
    public static final double NEW_MOON_MAX_ILLUMINATION = 0.10;
    public static final String NEW_MOON_REASON = "달이 거의 없는 그믐 무렵이에요";

    /** 달이 떠 있으면 100 − (달 밝기% × 0.9). 보름달(100%) → 10점, 반달(50%) → 55점 */
    public static final double MOON_PENALTY_PER_PERCENT = 0.9;
    public static final String BRIGHT_MOON_REASON = "달이 밝아서 희미한 별은 묻힐 수 있어요";
    /** 밤 중간에 달이 지면 붙이는 문장. %s에 달 지는 시각(예: 01:10)이 들어간다 */
    public static final String MOON_SETS_LATER_REASON = "달이 %s에 지면 더 좋아요";

    // ===== 구름 상한 (추가 규칙) =====
    // 구름이 하늘을 덮으면 공기나 달이 좋아도 별이 안 보인다.

    /** 구름 71~90%인 시간은 최대 30점 */
    public static final int HEAVY_CLOUD_MIN = 71;
    public static final int HEAVY_CLOUD_MAX_SCORE = 30;
    /** 구름 91% 이상이거나 비가 오는 시간은 0점 */
    public static final int OVERCAST_MIN = 91;
    /** 강수량이 이 값(mm) 이상이면 비가 오는 시간으로 본다 (Open-Meteo 최소 단위, 노을과 같은 기준) */
    public static final double RAIN_MIN_MM = 0.1;

    // ===== 밤 시간과 추천 구간 =====

    /** 밤 시간은 천문박명이 끝나는 시각부터 다음 날 새벽 이 시각(4시)까지 */
    public static final int NIGHT_END_HOUR = 4;

    /** 최고점과 이 점수 이내로 차이 나는 시간을 앞뒤로 이어 붙여 추천 구간을 만든다 */
    public static final int RECOMMEND_RANGE_POINTS = 5;

    /** 하루 대표 점수(시간별 최고점)가 이 값 미만이면 추천 구간 대신 쉬는 날 안내를 보여 준다 */
    public static final int REST_SCORE_BELOW = 30;
    public static final String REST_MESSAGE = "오늘 밤은 쉬어 가는 날이에요";
}
