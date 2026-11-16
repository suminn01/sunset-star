package com.sunsetstar.forecast;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.sunsetstar.forecast.NearbyRules.*;

/**
 * 근처 장소 추천의 점수 계산 (기능명세서 F5 "점수 계산").
 *
 * 후보 점수 = 날씨 점수(노을 F2 또는 별 F4와 같은 계산) + 장소 보정, 0~100으로 자른다.
 * 현재 위치도 같은 방식으로 점수를 매겨서 비교한다.
 *
 * 외부 호출은 하지 않는다. Open-Meteo 응답을 받아서 점수만 매기므로 테스트에서 가짜 응답으로 바로 확인할 수 있다.
 *
 * 구현 기준
 * - 노을 ③ 서쪽 하늘: 후보는 현재 위치에서 10km 안이라 해 지는 쪽 먼 하늘(25~100km)이 거의 같다.
 *   그래서 현재 위치의 서쪽 하늘 검사 결과를 후보에도 그대로 쓴다.
 * - 별 빛 공해 추정은 계수를 바꾸는 것이라 +15 제한에 넣지 않는다. +15 제한은 점수로 더하는 보정에만 적용한다.
 */
@Component
public class NearbyScorer {

    private final SunsetScoreService sunsetScoreService;
    private final StarScoreService starScoreService;

    public NearbyScorer(SunsetScoreService sunsetScoreService, StarScoreService starScoreService) {
        this.sunsetScoreService = sunsetScoreService;
        this.starScoreService = starScoreService;
    }

    /** 현재 위치와 후보들의 점수 */
    public record Scores(ScoredPlace current, List<ScoredPlace> candidates) {
    }

    // ===== 노을 =====

    /**
     * 노을: 현재 위치와 후보들의 그날 점수.
     *
     * @param candidateWeathers 후보 순서대로의 날씨 (후보 수와 같아야 한다)
     * @param candidateAirs     후보 순서대로의 미세먼지. 실패했으면 null (공기를 빼고 계산)
     * @return 현재 위치 점수를 못 내면 null
     */
    public Scores scoreSunset(LocalDate date, LightPollution userLight,
                              OpenMeteoForecastResponse currentWeather, OpenMeteoAirQualityResponse currentAir,
                              Map<LocalDate, WestSky> westSkies,
                              List<NearbyCandidate> candidates,
                              List<OpenMeteoForecastResponse> candidateWeathers,
                              List<OpenMeteoAirQualityResponse> candidateAirs) {
        Integer currentWeatherScore = sunsetScoreOn(date, currentWeather, currentAir, westSkies);
        if (currentWeatherScore == null) {
            return null;
        }
        // 현재 위치가 도심이면 건물에 서쪽이 가려질 수 있어서 −5
        int currentCorrection = userLight == LightPollution.CITY ? CITY_CURRENT_LOCATION_POINTS : 0;
        ScoredPlace current = new ScoredPlace(null, currentWeatherScore, currentCorrection,
                clamp(currentWeatherScore + currentCorrection), List.of(), false);

        Double currentElevation = currentWeather.elevation();
        List<ScoredPlace> scored = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            NearbyCandidate candidate = candidates.get(i);
            OpenMeteoForecastResponse weather = valueAt(candidateWeathers, i);
            Integer weatherScore = sunsetScoreOn(date, weather, valueAt(candidateAirs, i), westSkies);
            if (weatherScore == null) {
                continue; // 날씨를 못 받은 후보는 빼고 추천한다
            }

            int correction = 0;
            List<String> reasons = new ArrayList<>();
            boolean estimated = false;

            // 높은 곳: 현재 위치보다 50m 이상 +5, 150m 이상 +10 (Open-Meteo 고도, 실제 땅 높이)
            if (currentElevation != null && weather.elevation() != null) {
                double higher = weather.elevation() - currentElevation;
                if (higher >= SUNSET_MUCH_HIGHER_M) {
                    correction += SUNSET_MUCH_HIGHER_POINTS;
                    reasons.add(HIGHER_REASON);
                } else if (higher >= SUNSET_HIGHER_M) {
                    correction += SUNSET_HIGHER_POINTS;
                    reasons.add(HIGHER_REASON);
                }
            }
            // 트인 곳: 전망대·해수욕장·한강공원으로 찾은 곳 +5 (키워드로 짐작)
            if (OPEN_VIEW_KEYWORDS.contains(candidate.keyword())) {
                correction += OPEN_VIEW_POINTS;
                reasons.add(OPEN_VIEW_REASON);
                estimated = true;
            }

            correction = Math.min(correction, MAX_CORRECTION);
            scored.add(new ScoredPlace(candidate, weatherScore, correction, clamp(weatherScore + correction),
                    List.copyOf(reasons), estimated));
        }
        return new Scores(current, List.copyOf(scored));
    }

    /** 그날 노을 날씨 점수. 날씨가 없거나 그날 예보가 없으면 null */
    private Integer sunsetScoreOn(LocalDate date, OpenMeteoForecastResponse weather,
                                  OpenMeteoAirQualityResponse air, Map<LocalDate, WestSky> westSkies) {
        if (weather == null || weather.daily() == null || weather.hourly() == null) {
            return null;
        }
        for (DailySunsetForecast day : sunsetScoreService.forecast(weather, air, westSkies)) {
            if (day.date().equals(date)) {
                return day.score().score();
            }
        }
        return null;
    }

    // ===== 별 =====

    /**
     * 별: 현재 위치와 후보들의 그날 밤 점수.
     * 후보는 장소 종류로 빛 공해를 짐작해서 계산한다 (천문대·휴양림 시골, 캠핑장 교외).
     *
     * @return 현재 위치 점수를 못 내면 null
     */
    public Scores scoreStar(LocalDate date, LightPollution userLight, double latitude, double longitude,
                            OpenMeteoForecastResponse currentWeather, OpenMeteoAirQualityResponse currentAir,
                            List<NearbyCandidate> candidates,
                            List<OpenMeteoForecastResponse> candidateWeathers,
                            List<OpenMeteoAirQualityResponse> candidateAirs) {
        Integer currentWeatherScore = starScoreOn(date, currentWeather, currentAir, latitude, longitude, userLight);
        if (currentWeatherScore == null) {
            return null;
        }
        ScoredPlace current = new ScoredPlace(null, currentWeatherScore, 0, currentWeatherScore, List.of(), false);

        List<ScoredPlace> scored = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            NearbyCandidate candidate = candidates.get(i);
            OpenMeteoForecastResponse weather = valueAt(candidateWeathers, i);

            // 빛 공해 짐작: 키워드로 정해지면 그 값, 아니면 사용자가 고른 값
            LightPollution estimatedLight = ESTIMATED_LIGHT.getOrDefault(candidate.keyword(), userLight);
            Integer weatherScore = starScoreOn(date, weather, valueAt(candidateAirs, i),
                    candidate.coordinate().latitude(), candidate.coordinate().longitude(), estimatedLight);
            if (weatherScore == null) {
                continue;
            }

            int correction = 0;
            List<String> reasons = new ArrayList<>();
            boolean estimated = ESTIMATED_LIGHT.containsKey(candidate.keyword());
            if (estimated && estimatedLight.factor() > userLight.factor()) {
                reasons.add(DARK_REASON);
            }
            // 높은 곳: 해발 500m 이상 +5
            if (weather.elevation() != null && weather.elevation() >= STAR_HIGH_ALTITUDE_M) {
                correction += STAR_HIGH_ALTITUDE_POINTS;
                reasons.add(HIGH_ALTITUDE_REASON);
            }

            correction = Math.min(correction, MAX_CORRECTION);
            scored.add(new ScoredPlace(candidate, weatherScore, correction, clamp(weatherScore + correction),
                    List.copyOf(reasons), estimated));
        }
        return new Scores(current, List.copyOf(scored));
    }

    /** 그날 밤 별 대표 점수. 날씨가 없거나 그날 밤 예보가 없으면 null */
    private Integer starScoreOn(LocalDate date, OpenMeteoForecastResponse weather, OpenMeteoAirQualityResponse air,
                                double latitude, double longitude, LightPollution light) {
        if (weather == null || weather.hourly() == null) {
            return null;
        }
        DailyStarForecast night = starScoreService.forecastNight(date, weather.hourly(), air, latitude, longitude, light);
        return night == null ? null : night.score();
    }

    // ===== 도우미 =====

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private <T> T valueAt(List<T> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }
}
