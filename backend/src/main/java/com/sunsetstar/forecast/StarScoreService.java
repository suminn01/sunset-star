package com.sunsetstar.forecast;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static com.sunsetstar.forecast.StarScoreRules.*;

/**
 * 별 점수를 계산한다 (기능명세서 F4).
 *
 * 하는 일은 세 가지다.
 * - forecast: Open-Meteo 응답 → 날짜마다 밤 시간(천문박명 끝 ~ 04시)을 1시간씩 나눠 점수 → 하룻밤 요약 (7일치)
 * - calculateHour: 한 시간치 값(StarHourConditions) → 그 시간의 별 점수
 * - summarize: 시간별 점수 → 대표 점수(최고값), 추천 구간, 쉬는 날 안내
 *
 * 노을과 달리 해·달 계산이 필요해서 SkyCalculator를 받아 쓴다.
 * Open-Meteo를 직접 부르지는 않는다 (4단계 Controller가 응답을 넘겨준다).
 */
@Service
public class StarScoreService {

    static final String CLOUD_NAME = "구름";
    static final String AIR_NAME = "공기";
    static final String MOON_NAME = "달";

    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private final SkyCalculator sky;

    public StarScoreService(SkyCalculator sky) {
        this.sky = sky;
    }

    // ===== 7일치 예보 =====

    /**
     * Open-Meteo 응답으로 날짜마다 별 예보를 만든다.
     *
     * @param airQuality 미세먼지 응답. 실패했으면 null로 넘겨도 되고, 그때는 공기를 빼고 계산한다
     */
    public List<DailyStarForecast> forecast(OpenMeteoForecastResponse weather,
                                            OpenMeteoAirQualityResponse airQuality,
                                            double latitude, double longitude,
                                            LightPollution light) {
        List<DailyStarForecast> nights = new ArrayList<>();
        for (LocalDate date : weather.daily().time()) {
            DailyStarForecast night = forecastNight(date, weather.hourly(), airQuality, latitude, longitude, light);
            if (night != null) {
                nights.add(night);
            }
        }
        return nights;
    }

    /**
     * 하룻밤 예보. 밤 시간은 천문박명이 끝나는 시각부터 다음 날 04시까지이고,
     * 그 사이의 정각마다 점수를 매긴다. 예: 천문박명 끝 18:56 → 19:00, 20:00, … 03:00 (9칸)
     *
     * @return 계산할 수 있는 시간이 하나도 없으면 null (예: 예보 마지막 날 밤은 데이터가 끊긴다)
     */
    DailyStarForecast forecastNight(LocalDate date, OpenMeteoForecastResponse.Hourly hourly,
                                    OpenMeteoAirQualityResponse airQuality,
                                    double latitude, double longitude, LightPollution light) {
        LocalDateTime nightStart = sky.astronomicalDusk(date, latitude, longitude);
        if (nightStart == null) {
            return null;
        }
        LocalDateTime nightEnd = date.plusDays(1).atTime(NIGHT_END_HOUR, 0);

        List<HourlyStarScore> hours = new ArrayList<>();
        for (LocalDateTime time = ceilHour(nightStart); time.isBefore(nightEnd); time = time.plusHours(1)) {
            int i = hourly.time().indexOf(time);
            if (i < 0) {
                continue; // 예보 범위 밖 시간은 건너뛴다
            }
            SkyCalculator.Moon moon = sky.moonAt(time, latitude, longitude);
            hours.add(calculateHour(conditionsAt(hourly, airQuality, i, time, moon), light));
        }
        if (hours.isEmpty()) {
            return null;
        }
        String moonNote = moonNote(nightStart, nightEnd, latitude, longitude);
        return summarize(date, nightStart, hours, light, moonNote);
    }

    /** 시간별 예보의 i번째 값과 달 정보를 StarHourConditions로 묶는다. */
    private StarHourConditions conditionsAt(OpenMeteoForecastResponse.Hourly hourly,
                                            OpenMeteoAirQualityResponse airQuality,
                                            int i, LocalDateTime time, SkyCalculator.Moon moon) {
        Double visibilityM = valueAt(hourly.visibility(), i);
        Double pm25 = null;
        if (airQuality != null && airQuality.hourly() != null) {
            pm25 = valueAt(airQuality.hourly().pm25(), airQuality.hourly().time().indexOf(time));
        }
        return new StarHourConditions(
                time,
                valueAt(hourly.cloudCover(), i),
                pm25,
                valueAt(hourly.humidity(), i),
                visibilityM == null ? null : visibilityM / 1000.0,
                valueAt(hourly.precipitation(), i),
                moon
        );
    }

    /**
     * 초저녁(천문박명 끝)에 떠 있던 달이 04시 전에 지면 "달이 01:10에 지면 더 좋아요"를 만든다.
     */
    private String moonNote(LocalDateTime nightStart, LocalDateTime nightEnd, double latitude, double longitude) {
        if (!sky.moonAt(nightStart, latitude, longitude).isUp()) {
            return null;
        }
        LocalDateTime moonSet = sky.nextMoonSet(nightStart, latitude, longitude);
        if (moonSet == null || !moonSet.isBefore(nightEnd)) {
            return null;
        }
        return String.format(MOON_SETS_LATER_REASON, moonSet.format(HOUR_MINUTE));
    }

    // ===== 대표 점수와 추천 구간 =====

    /**
     * 시간별 점수로 하룻밤을 요약한다.
     * - 대표 점수 = 시간별 점수 중 가장 높은 값
     * - 추천 구간 = 가장 높은 시간에서 시작해, 최고점과 5점 이내인 시간을 앞뒤로 이어 붙인다
     *   예: 23시 70, 00시 78, 01시 80, 02시 76, 03시 60 → 00:00 ~ 03:00 (최고 80, 75점 이상인 시간)
     * - 대표 점수가 30점 미만이면 추천 구간 대신 "오늘 밤은 쉬어 가는 날이에요"
     */
    public DailyStarForecast summarize(LocalDate date, LocalDateTime nightStart, List<HourlyStarScore> hours,
                                       LightPollution light, String moonNote) {
        int bestIndex = 0;
        for (int i = 1; i < hours.size(); i++) {
            if (hours.get(i).score() > hours.get(bestIndex).score()) {
                bestIndex = i;
            }
        }
        int best = hours.get(bestIndex).score();
        Grade grade = Grade.of(best);

        if (best < REST_SCORE_BELOW) {
            return new DailyStarForecast(date, nightStart, hours, best, grade,
                    null, null, REST_MESSAGE, moonNote, null);
        }

        int from = bestIndex;
        int to = bestIndex;
        while (from - 1 >= 0 && hours.get(from - 1).score() >= best - RECOMMEND_RANGE_POINTS) {
            from--;
        }
        while (to + 1 < hours.size() && hours.get(to + 1).score() >= best - RECOMMEND_RANGE_POINTS) {
            to++;
        }

        // 각 칸은 그 정각부터 1시간이다. 01:00 칸이 마지막이면 02:00까지 추천한다.
        LocalDateTime recommendFrom = hours.get(from).time();
        LocalDateTime recommendTo = hours.get(to).time().plusHours(1);

        return new DailyStarForecast(date, nightStart, hours, best, grade,
                recommendFrom, recommendTo, null, moonNote, lightNote(light, best));
    }

    /** 빛 공해 이유 문장. 도심은 70점 이상일 때만 보여 준다. */
    private String lightNote(LightPollution light, int score) {
        if (light == LightPollution.CITY && score < LightPollution.CITY_GOOD_NIGHT_MIN_SCORE) {
            return null;
        }
        return light.reason();
    }

    // ===== 한 시간 점수 =====

    /**
     * 한 시간의 별 점수.
     * (① 구름 × 0.5 + ② 공기 × 0.2 + ③ 달 × 0.3) × 빛 공해 계수 → 반올림 → 구름 상한 규칙
     */
    public HourlyStarScore calculateHour(StarHourConditions c, LightPollution light) {
        List<Factor> factors = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        double weightedSum = 0;
        double weightTotal = 0;

        // ① 구름
        if (c.cloudCover() == null) {
            missing.add(CLOUD_NAME);
        } else {
            ScoreBand band = ScoreBand.find(CLOUD, c.cloudCover());
            factors.add(Factor.from(CLOUD_NAME, band));
            weightedSum += band.points() * CLOUD_WEIGHT;
            weightTotal += CLOUD_WEIGHT;
        }

        // ② 공기: PM2.5 구간 점수에서, 습하거나 뿌옇면 30점을 더 뺀다
        if (c.pm25() == null) {
            missing.add(AIR_NAME);
        } else {
            ScoreBand band = ScoreBand.find(AIR, (int) Math.round(c.pm25()));
            int airScore = band.points();
            String reason = band.reason();
            if (isHazy(c.humidity(), c.visibilityKm())) {
                airScore = Math.max(0, airScore - HAZE_PENALTY);
                reason = HAZE_REASON;
            }
            factors.add(new Factor(AIR_NAME, airScore, reason));
            weightedSum += airScore * AIR_WEIGHT;
            weightTotal += AIR_WEIGHT;
        }

        // ③ 달 (해·달 계산이라 항상 값이 있다)
        double moonScore = moonScore(c.moon());
        factors.add(new Factor(MOON_NAME, (int) Math.round(moonScore), moonReason(c.moon())));
        weightedSum += moonScore * MOON_WEIGHT;
        weightTotal += MOON_WEIGHT;

        // 빠진 요소가 있으면 남은 요소의 비중으로 다시 나눈다 (예: 공기가 없으면 (구름×0.5 + 달×0.3) ÷ 0.8)
        double base = missing.isEmpty() ? weightedSum : weightedSum / weightTotal;
        int score = (int) Math.round(base * light.factor());

        // 구름 상한: 구름이 하늘을 덮으면 공기나 달이 좋아도 별이 안 보인다
        if (c.cloudCover() != null && c.cloudCover() >= OVERCAST_MIN) {
            score = 0;
        } else if (c.cloudCover() != null && c.cloudCover() >= HEAVY_CLOUD_MIN) {
            score = Math.min(score, HEAVY_CLOUD_MAX_SCORE);
        }
        if (c.precipitation() != null && c.precipitation() >= RAIN_MIN_MM) {
            score = 0;
        }

        return new HourlyStarScore(c.time(), score, List.copyOf(factors), List.copyOf(missing));
    }

    private boolean isHazy(Integer humidity, Double visibilityKm) {
        return (humidity != null && humidity >= HAZE_HUMIDITY_MIN)
                || (visibilityKm != null && visibilityKm < HAZE_VISIBILITY_KM);
    }

    /** 달 점수: 진 시간 100, 그믐 무렵 100, 떠 있으면 100 − (밝기% × 0.9) */
    private double moonScore(SkyCalculator.Moon moon) {
        if (!moon.isUp() || moon.illumination() < NEW_MOON_MAX_ILLUMINATION) {
            return MOON_DOWN_SCORE;
        }
        return 100 - (moon.illumination() * 100 * MOON_PENALTY_PER_PERCENT);
    }

    private String moonReason(SkyCalculator.Moon moon) {
        if (!moon.isUp()) {
            return MOON_DOWN_REASON;
        }
        if (moon.illumination() < NEW_MOON_MAX_ILLUMINATION) {
            return NEW_MOON_REASON;
        }
        return BRIGHT_MOON_REASON;
    }

    // ===== 도우미 =====

    /** 다음 정각으로 올린다. 18:56 → 19:00, 19:00 → 19:00 */
    private LocalDateTime ceilHour(LocalDateTime time) {
        LocalDateTime hour = time.truncatedTo(ChronoUnit.HOURS);
        return hour.equals(time) ? hour : hour.plusHours(1);
    }

    private <T> T valueAt(List<T> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }
}
