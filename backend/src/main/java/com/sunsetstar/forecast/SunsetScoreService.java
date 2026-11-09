package com.sunsetstar.forecast;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.sunsetstar.forecast.SunsetScoreRules.*;

/**
 * 노을 점수를 계산한다 (기능명세서 F2).
 *
 * 노을 점수 = 50 + ①위쪽 구름 + ②낮은 구름 + ③서쪽 하늘 + ④공기 + ⑤습도·시정 + ⑥비, 0~100으로 자름
 *
 * 기준 숫자와 이유 문장은 SunsetScoreRules에 있고, 이 클래스는 "어떤 순서로 무엇을 비교하는지"만 담는다.
 * 같은 입력이면 항상 같은 점수가 나온다 (외부 호출 없음, 현재 시각을 쓰지 않음).
 */
@Service
public class SunsetScoreService {

    // 요소 이름 (화면과 기록에 쓰인다)
    static final String UPPER_CLOUD_NAME = "위쪽 구름";
    static final String LOW_CLOUD_NAME = "낮은 구름";
    static final String WEST_SKY_NAME = "서쪽 하늘";
    static final String AIR_NAME = "공기";
    static final String HUMIDITY_NAME = "습도·시정";
    static final String RAIN_NAME = "비";

    public SunsetScore calculate(SunsetConditions c) {
        List<Factor> factors = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        // ① 위쪽 구름: 높은 구름과 중간 구름 중 큰 값
        Integer upperCloud = maxOrNull(c.cloudCoverHigh(), c.cloudCoverMid());
        if (upperCloud == null) {
            missing.add(UPPER_CLOUD_NAME);
        } else if (c.cloudCoverLow() != null && c.cloudCoverLow() >= LOW_CLOUD_HIDES_UPPER) {
            // 추가 규칙: 낮은 구름이 두꺼우면 위쪽 구름이 보이지 않으므로 0점
            factors.add(new Factor(UPPER_CLOUD_NAME, 0, null));
        } else {
            factors.add(Factor.from(UPPER_CLOUD_NAME, ScoreBand.find(UPPER_CLOUD, upperCloud)));
        }

        // ② 낮은 구름 (내 위치)
        if (c.cloudCoverLow() == null) {
            missing.add(LOW_CLOUD_NAME);
        } else {
            factors.add(Factor.from(LOW_CLOUD_NAME, ScoreBand.find(LOW_CLOUD, c.cloudCoverLow())));
        }

        // ③ 서쪽 하늘: 5단계(F3) 전까지는 값이 없어서 0점으로 둔다. "정보 없음"으로 보지 않는다.
        if (c.westSkyLowCloud() != null) {
            factors.add(Factor.from(WEST_SKY_NAME, ScoreBand.find(WEST_SKY, c.westSkyLowCloud())));
        }

        // ④ 공기: PM2.5를 반올림한 정수로 구간을 찾는다 (15.4 → 15 → 좋음)
        if (c.pm25() == null) {
            missing.add(AIR_NAME);
        } else {
            int pm25 = (int) Math.round(c.pm25());
            factors.add(Factor.from(AIR_NAME, ScoreBand.find(AIR, pm25)));
        }

        // ⑤ 습도·시정: 더 나쁜 쪽(안개, −10)을 먼저 확인한다
        if (c.humidity() == null && c.visibilityKm() == null) {
            missing.add(HUMIDITY_NAME);
        } else {
            factors.add(humidityFactor(c.humidity(), c.visibilityKm()));
        }

        // 여기까지 더한 점수를 0~100으로 자른다
        int score = clamp(BASE_SCORE + sumPoints(factors));

        // ⑥ 비: 일몰 때 비가 오면 최대 20점, 그친 뒤면 +15
        if (c.rainAtSunsetMm() == null) {
            missing.add(RAIN_NAME);
        } else if (c.rainAtSunsetMm() >= RAIN_AT_SUNSET_MIN_MM) {
            int capped = Math.min(score, RAIN_AT_SUNSET_MAX_SCORE);
            // 깎인 만큼을 비 요소의 점수로 기록한다 (예: 70점이 20점이 되면 −50)
            factors.add(new Factor(RAIN_NAME, capped - score, RAIN_AT_SUNSET_REASON));
            score = capped;
        } else if (c.rainBeforeSunsetMm() != null && c.rainBeforeSunsetMm() >= AFTER_RAIN_TOTAL_MIN_MM) {
            factors.add(new Factor(RAIN_NAME, AFTER_RAIN_POINTS, AFTER_RAIN_REASON));
            score = clamp(score + AFTER_RAIN_POINTS);
        }

        return new SunsetScore(score, Grade.of(score), List.copyOf(factors), List.copyOf(missing));
    }

    /** ⑤ 습도·시정 요소. 둘 중 하나만 있어도 있는 값으로 판단한다. */
    private Factor humidityFactor(Integer humidity, Double visibilityKm) {
        boolean foggy = (humidity != null && humidity >= FOG_HUMIDITY_MIN)
                || (visibilityKm != null && visibilityKm < FOG_VISIBILITY_KM);
        if (foggy) {
            return new Factor(HUMIDITY_NAME, FOG_POINTS, FOG_REASON);
        }
        if (humidity != null && humidity >= DAMP_HUMIDITY_MIN) {
            return new Factor(HUMIDITY_NAME, DAMP_POINTS, DAMP_REASON);
        }
        return new Factor(HUMIDITY_NAME, 0, null);
    }

    /** 둘 중 큰 값. 하나만 있으면 그 값, 둘 다 없으면 null */
    private Integer maxOrNull(Integer a, Integer b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return Math.max(a, b);
    }

    private int sumPoints(List<Factor> factors) {
        int sum = 0;
        for (Factor factor : factors) {
            sum += factor.points();
        }
        return sum;
    }

    private int clamp(int score) {
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, score));
    }
}
