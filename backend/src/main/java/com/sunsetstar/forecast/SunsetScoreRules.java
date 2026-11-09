package com.sunsetstar.forecast;

import java.util.List;

/**
 * 노을 점수(기능명세서 F2)의 기준 숫자와 이유 문장을 한곳에 모은 클래스.
 *
 * 노을 점수 = 50 + ①위쪽 구름 + ②낮은 구름 + ③서쪽 하늘 + ④공기 + ⑤습도·시정 + ⑥비
 * → 마지막에 0~100으로 자른다.
 *
 * 숫자는 모두 기능명세서 Ver.1.5의 초기값이다. F7 관측 기록을 보면서 바꿀 때는 이 파일만 고친다.
 * 계산 순서와 방법은 SunsetScoreService에 있다.
 */
public final class SunsetScoreRules {

    // 상수만 모아 둔 클래스라 객체를 만들 일이 없다.
    private SunsetScoreRules() {
    }

    // ===== 기본 점수와 범위 =====

    public static final int BASE_SCORE = 50;
    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 100;

    // ===== ① 위쪽 구름 (높은 구름과 중간 구름 중 큰 값, %) =====

    public static final List<ScoreBand> UPPER_CLOUD = List.of(
            new ScoreBand(0, 9, 5, "구름 없이 맑아서 주황빛 그라데이션을 볼 수 있어요"),
            new ScoreBand(10, 29, 10, "옅은 구름이 조금 있어서 색이 살짝 물들 것 같아요"),
            new ScoreBand(30, 70, 25, "높은 구름이 적당해서 분홍빛이 예쁘게 번질 것 같아요"),
            new ScoreBand(71, 89, 5, "구름이 많은 편이라 빛이 일부만 들어올 것 같아요"),
            new ScoreBand(90, 100, -15, "구름이 하늘을 꽉 덮어서 빛이 들어올 틈이 없어요")
    );

    /** 추가 규칙: 낮은 구름이 이 값(%) 이상이면 위쪽 구름이 가려 보이지 않으므로 ① 점수를 0으로 한다. */
    public static final int LOW_CLOUD_HIDES_UPPER = 80;

    // ===== ② 낮은 구름 (내 위치, %) =====

    public static final List<ScoreBand> LOW_CLOUD = List.of(
            new ScoreBand(0, 19, 0, null),
            new ScoreBand(20, 49, -10, "낮은 구름이 조금 있어서 일부를 가릴 수 있어요"),
            new ScoreBand(50, 79, -25, "낮은 구름이 많아서 노을을 가릴 것 같아요"),
            new ScoreBand(80, 100, -40, "낮은 구름이 두껍게 덮여 있어요")
    );

    // ===== ③ 서쪽 하늘 (세 지점 낮은 구름 중 최댓값, %) =====
    // 5단계(be/west-sky, F3)에서 쓴다. 그 전까지 ③은 0점이다.

    public static final List<ScoreBand> WEST_SKY = List.of(
            new ScoreBand(0, 29, 10, "해 지는 쪽 하늘이 트여 있어요"),
            new ScoreBand(30, 59, 0, null),
            new ScoreBand(60, 100, -20, "해 지는 쪽에 구름 벽이 있어서 빛이 막힐 수 있어요")
    );

    // ===== ④ 공기 (PM2.5, μg/m³) =====
    // Open-Meteo는 15.4처럼 소수로 주므로 반올림한 정수로 구간을 찾는다.

    public static final List<ScoreBand> AIR = List.of(
            new ScoreBand(0, 15, 5, "공기가 깨끗해서 색이 선명할 것 같아요"),
            new ScoreBand(16, 35, 0, null),
            new ScoreBand(36, 75, -10, "미세먼지 때문에 색이 탁해 보일 수 있어요"),
            new ScoreBand(76, Integer.MAX_VALUE, -20, "미세먼지가 심해서 하늘이 뿌옇게 보일 것 같아요")
    );

    // ===== ⑤ 습도·시정 =====
    // 두 값을 함께 봐야 해서 구간표 대신 숫자로 둔다. 더 나쁜 쪽(−10)을 먼저 확인한다.

    /** 습도 이 값(%) 이상 또는 시정 FOG_VISIBILITY_KM 미만이면 −10 */
    public static final int FOG_HUMIDITY_MIN = 90;
    public static final double FOG_VISIBILITY_KM = 5.0;
    public static final int FOG_POINTS = -10;
    public static final String FOG_REASON = "안개나 습기 때문에 멀리까지 잘 안 보일 것 같아요";

    /** 습도 80~89%면 −5 */
    public static final int DAMP_HUMIDITY_MIN = 80;
    public static final int DAMP_POINTS = -5;
    public static final String DAMP_REASON = "습해서 조금 흐릿할 수 있어요";

    // ===== ⑥ 비 =====

    /** 일몰 전 몇 시간을 "비 갠 뒤" 확인 범위로 볼지 */
    public static final int AFTER_RAIN_HOURS_BEFORE_SUNSET = 6;
    /** 그 시간 동안 내린 비의 합이 이 값(mm) 이상이고, 일몰 때 비가 그쳤으면 +15 */
    public static final double AFTER_RAIN_TOTAL_MIN_MM = 0.5;
    public static final int AFTER_RAIN_POINTS = 15;
    public static final String AFTER_RAIN_REASON = "비 갠 뒤라 공기가 깨끗해요. 노을이 좋은 경우가 많아요";

    /** 일몰 시각 강수량이 이 값(mm) 이상이면 "일몰 때 비가 옴"으로 본다. Open-Meteo 최소 단위가 0.1mm다. */
    public static final double RAIN_AT_SUNSET_MIN_MM = 0.1;
    /** 일몰 때 비가 오면 다른 요소가 좋아도 최종 점수는 이 값을 넘지 않는다. */
    public static final int RAIN_AT_SUNSET_MAX_SCORE = 20;
    public static final String RAIN_AT_SUNSET_REASON = "일몰 무렵 비 소식이 있어요";

    // ===== 추천 시간 =====

    /** 일몰 15분 전부터 일몰 20분 후까지 추천한다. 예: 일몰 18:20 → 18:05 ~ 18:40 */
    public static final int RECOMMEND_MINUTES_BEFORE_SUNSET = 15;
    public static final int RECOMMEND_MINUTES_AFTER_SUNSET = 20;
    /** 추천 시간과 함께 보여 줄 안내. 노을 색은 해가 진 직후에 구름이 물드는 경우가 많다. */
    public static final String STAY_AFTER_SUNSET_TIP = "해가 진 뒤에도 바로 떠나지 마세요";

    /** 점수가 이 값 미만이면 추천 시간 대신 쉬는 날 안내를 보여 준다. */
    public static final int REST_SCORE_BELOW = 30;
    public static final String REST_MESSAGE = "오늘은 쉬어요";
}
