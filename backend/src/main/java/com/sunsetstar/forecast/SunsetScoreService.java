package com.sunsetstar.forecast;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.sunsetstar.forecast.SunsetScoreRules.*;

/**
 * 노을 점수를 계산한다 (기능명세서 F2).
 *
 * 노을 점수 = 50 + ①위쪽 구름 + ②낮은 구름 + ③서쪽 하늘 + ④공기 + ⑤습도·시정 + ⑥비, 0~100으로 자름
 *
 * 기준 숫자와 이유 문장은 SunsetScoreRules에 있고, 이 클래스는 "어떤 순서로 무엇을 비교하는지"만 담는다.
 * 같은 입력이면 항상 같은 점수가 나온다 (외부 호출 없음, 현재 시각을 쓰지 않음).
 *
 * 하는 일은 세 가지다.
 * - forecast: Open-Meteo 응답(+ 서쪽 하늘 검사) → 날짜마다 일몰 시각 값 고르기 → 점수 → 추천 시간 (7일치)
 * - calculate: 일몰 시각 값(SunsetConditions) → 점수
 * - recommend: 일몰 시각 + 점수 → 추천 시간 또는 쉬는 날 안내
 *
 * Open-Meteo를 직접 부르지는 않는다. 응답을 받아 넘기는 일은 4단계 Controller가 한다.
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


    // ===== 7일치 예보 =====

    /**
     * Open-Meteo 응답으로 날짜마다 노을 예보를 만든다.
     *
     * @param weather    날씨 응답 (필수)
     * @param airQuality 미세먼지 응답. 대기질 API가 실패했으면 null로 넘겨도 되고, 그때는 ④ 공기를 빼고 계산한다
     */
    public List<DailySunsetForecast> forecast(OpenMeteoForecastResponse weather,
                                              OpenMeteoAirQualityResponse airQuality) {
        return forecast(weather, airQuality, Map.of());
    }

    /**
     * 서쪽 하늘 검사 결과까지 넣어서 날짜마다 노을 예보를 만든다.
     *
     * @param westSkies 날짜 → 해 지는 쪽 하늘 검사 결과(WestSkyService). 없는 날짜는 ③을 0점으로 둔다
     */
    public List<DailySunsetForecast> forecast(OpenMeteoForecastResponse weather,
                                              OpenMeteoAirQualityResponse airQuality,
                                              Map<LocalDate, WestSky> westSkies) {
        OpenMeteoForecastResponse.Daily daily = weather.daily();
        List<DailySunsetForecast> days = new ArrayList<>();

        for (int day = 0; day < daily.time().size(); day++) {
            LocalDate date = daily.time().get(day);
            LocalDateTime sunset = valueAt(daily.sunset(), day);
            if (sunset == null) {
                continue; // 일몰 시각이 없으면 그날은 계산할 수 없다
            }
            WestSky westSky = westSkies.get(date);
            Integer westSkyLowCloud = westSky == null ? null : westSky.maxLowCloud();
            SunsetConditions conditions = conditionsAtSunset(weather.hourly(), airQuality, sunset, westSkyLowCloud);
            SunsetScore score = calculate(conditions);
            days.add(recommend(date, sunset, score));
        }
        return days;
    }

    /**
     * 시간별 예보에서 일몰 시각의 값을 골라 SunsetConditions로 만든다.
     *
     * 시간별 예보는 정각(17:00, 18:00) 값만 있어서, 일몰 시각에서 가장 가까운 정각을 쓴다.
     * 예: 일몰 17:32 → 18:00 값, 일몰 17:20 → 17:00 값
     */
    SunsetConditions conditionsAtSunset(OpenMeteoForecastResponse.Hourly hourly,
                                        OpenMeteoAirQualityResponse airQuality,
                                        LocalDateTime sunset,
                                        Integer westSkyLowCloud) {
        LocalDateTime sunsetHour = nearestHour(sunset);
        int i = hourly.time().indexOf(sunsetHour);

        Double visibilityM = valueAt(hourly.visibility(), i);
        Double visibilityKm = visibilityM == null ? null : visibilityM / 1000.0;

        // 일몰 정각 바로 앞 6시간의 강수량 합 (예: 일몰 18:00 → 12:00~17:00 값 6개)
        Double rainBefore = sumBefore(hourly.precipitation(), i, AFTER_RAIN_HOURS_BEFORE_SUNSET);

        Double pm25 = null;
        if (airQuality != null && airQuality.hourly() != null) {
            int airIndex = airQuality.hourly().time().indexOf(sunsetHour);
            pm25 = valueAt(airQuality.hourly().pm25(), airIndex);
        }

        return new SunsetConditions(
                valueAt(hourly.cloudCoverHigh(), i),
                valueAt(hourly.cloudCoverMid(), i),
                valueAt(hourly.cloudCoverLow(), i),
                westSkyLowCloud, // ③ 서쪽 하늘: 세 지점 낮은 구름 최댓값 (검사 실패 시 null → 0점)
                pm25,
                valueAt(hourly.humidity(), i),
                visibilityKm,
                valueAt(hourly.precipitation(), i),
                rainBefore
        );
    }

    // ===== 추천 시간 =====

    /**
     * 추천 시간을 정한다. 일몰 15분 전 ~ 일몰 20분 후 (예: 일몰 18:20 → 18:05 ~ 18:40).
     * 점수가 30점 미만이면 추천 시간 대신 "오늘은 쉬어요"를 보여 준다.
     */
    public DailySunsetForecast recommend(LocalDate date, LocalDateTime sunset, SunsetScore score) {
        if (score.score() < REST_SCORE_BELOW) {
            return new DailySunsetForecast(date, sunset, score, null, null, REST_MESSAGE);
        }
        return new DailySunsetForecast(
                date,
                sunset,
                score,
                sunset.minusMinutes(RECOMMEND_MINUTES_BEFORE_SUNSET),
                sunset.plusMinutes(RECOMMEND_MINUTES_AFTER_SUNSET),
                STAY_AFTER_SUNSET_TIP
        );
    }

    // ===== 점수 계산 =====

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

    /** 가장 가까운 정각. 30분 이상이면 다음 정각으로 올린다. 서쪽 하늘 검사(WestSkyService)도 같은 기준을 쓴다. */
    static LocalDateTime nearestHour(LocalDateTime time) {
        LocalDateTime hour = time.truncatedTo(ChronoUnit.HOURS);
        return time.getMinute() >= 30 ? hour.plusHours(1) : hour;
    }

    /** 목록의 index번째 값. 목록이 없거나 index가 범위 밖이면 null */
    private <T> T valueAt(List<T> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    /** index 바로 앞 hours개 값의 합. 빈 값은 0으로 본다. 하나도 읽을 수 없으면 null */
    private Double sumBefore(List<Double> values, int index, int hours) {
        if (values == null || index < 0) {
            return null;
        }
        double sum = 0;
        boolean found = false;
        for (int k = Math.max(0, index - hours); k < index; k++) {
            Double value = valueAt(values, k);
            if (value != null) {
                sum += value;
                found = true;
            }
        }
        return found ? sum : null;
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
