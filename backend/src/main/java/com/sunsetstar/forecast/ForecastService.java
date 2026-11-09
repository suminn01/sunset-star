package com.sunsetstar.forecast;

import com.sunsetstar.global.ApiException;
import com.sunsetstar.global.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 7일 예보와 상세 예보를 만든다 (기능명세서 F6). Controller가 부르는 곳이다.
 *
 * 순서
 * 1. OpenMeteoClient로 날씨와 미세먼지를 받는다
 * 2. SunsetScoreService, StarScoreService로 날짜마다 노을·별 점수를 계산한다
 * 3. 화면 모양으로 바꾼다
 *    - forecast: 7일 카드 (등급 이름, 이유 한 줄, 4일째부터 "참고용")
 *    - detail: 하루 상세 (노을 요소별 점수, 별 시간대별 점수, 달 정보)
 *
 * 좌표는 소수점 둘째 자리로 반올림해서 쓴다(F6). 37.56651과 37.56649가 같은 37.57이 되어
 * 캐시를 함께 쓴다. 둘째 자리는 약 1km 간격이라 날씨 예보에는 차이가 없다.
 *
 * 실패했을 때 (기능명세서 F8)
 * - 날씨 API: 한 번 더 시도하고, 그래도 실패하면 503 WEATHER_UNAVAILABLE ("하늘 정보를 못 가져왔어요")
 * - 미세먼지 API: 공기를 빼고 계산한다 ("일부 데이터 없음")
 */
@Service
public class ForecastService {

    private static final Logger log = LoggerFactory.getLogger(ForecastService.class);

    /** 좌표 반올림 자리: 소수점 둘째 자리 → 100 */
    private static final double COORDINATE_SCALE = 100.0;

    /** 4일째(index 3)부터 "참고용" */
    static final int REFERENCE_FROM_DAY_INDEX = 3;

    private final OpenMeteoClient client;
    private final SunsetScoreService sunsetScoreService;
    private final StarScoreService starScoreService;
    private final SkyCalculator sky;

    public ForecastService(OpenMeteoClient client,
                           SunsetScoreService sunsetScoreService,
                           StarScoreService starScoreService,
                           SkyCalculator sky) {
        this.client = client;
        this.sunsetScoreService = sunsetScoreService;
        this.starScoreService = starScoreService;
        this.sky = sky;
    }

    // ===== 7일 예보 =====

    /** 좌표와 빛 공해 단계로 7일 예보를 만든다. */
    public ForecastResponse forecast(double latitude, double longitude, LightPollution light) {
        latitude = roundCoordinate(latitude);
        longitude = roundCoordinate(longitude);
        OpenMeteoForecastResponse weather = fetchWeather(latitude, longitude);
        OpenMeteoAirQualityResponse airQuality = fetchAirQualityOrNull(latitude, longitude);
        return toResponse(weather, airQuality, latitude, longitude, light, now());
    }

    /**
     * Open-Meteo 응답으로 7일 카드를 만든다. 외부 호출이 없어서 테스트에서 바로 부를 수 있다.
     */
    ForecastResponse toResponse(OpenMeteoForecastResponse weather, OpenMeteoAirQualityResponse airQuality,
                                double latitude, double longitude, LightPollution light,
                                LocalDateTime basedAt) {
        Scores scores = calculate(weather, airQuality, latitude, longitude, light);

        List<ForecastResponse.DayCard> days = new ArrayList<>();
        List<LocalDate> dates = weather.daily().time();
        for (int i = 0; i < dates.size(); i++) {
            LocalDate date = dates.get(i);
            days.add(new ForecastResponse.DayCard(
                    date,
                    i >= REFERENCE_FROM_DAY_INDEX,
                    toSunsetCard(scores.sunsets().get(date)),
                    toStarCard(scores.stars().get(date))
            ));
        }
        return new ForecastResponse(latitude, longitude, light, light.label(), basedAt, List.copyOf(days));
    }

    // ===== 하루 상세 =====

    /**
     * 하루 상세 예보를 만든다.
     *
     * @throws ApiException 400 INVALID_PARAMETER: 예보 범위(오늘부터 7일) 밖의 날짜일 때
     */
    public ForecastDetailResponse detail(double latitude, double longitude, LightPollution light, LocalDate date) {
        latitude = roundCoordinate(latitude);
        longitude = roundCoordinate(longitude);
        OpenMeteoForecastResponse weather = fetchWeather(latitude, longitude);
        OpenMeteoAirQualityResponse airQuality = fetchAirQualityOrNull(latitude, longitude);
        return toDetail(weather, airQuality, latitude, longitude, light, date, now());
    }

    /** Open-Meteo 응답으로 하루 상세를 만든다. 외부 호출이 없어서 테스트에서 바로 부를 수 있다. */
    ForecastDetailResponse toDetail(OpenMeteoForecastResponse weather, OpenMeteoAirQualityResponse airQuality,
                                    double latitude, double longitude, LightPollution light,
                                    LocalDate date, LocalDateTime basedAt) {
        int dayIndex = weather.daily().time().indexOf(date);
        if (dayIndex < 0) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER,
                    "예보가 있는 날짜가 아니에요. 오늘부터 7일 안의 날짜를 골라 주세요: " + date);
        }
        Scores scores = calculate(weather, airQuality, latitude, longitude, light);

        return new ForecastDetailResponse(
                latitude,
                longitude,
                light,
                light.label(),
                basedAt,
                date,
                dayIndex >= REFERENCE_FROM_DAY_INDEX,
                toSunsetDetail(scores.sunsets().get(date)),
                toStarDetail(scores.stars().get(date), latitude, longitude)
        );
    }

    // ===== 공통 =====

    /** 날짜별 노을·별 계산 결과 */
    private record Scores(Map<LocalDate, DailySunsetForecast> sunsets, Map<LocalDate, DailyStarForecast> stars) {
    }

    private Scores calculate(OpenMeteoForecastResponse weather, OpenMeteoAirQualityResponse airQuality,
                             double latitude, double longitude, LightPollution light) {
        Map<LocalDate, DailySunsetForecast> sunsets = new HashMap<>();
        for (DailySunsetForecast day : sunsetScoreService.forecast(weather, airQuality)) {
            sunsets.put(day.date(), day);
        }
        Map<LocalDate, DailyStarForecast> stars = new HashMap<>();
        for (DailyStarForecast night : starScoreService.forecast(weather, airQuality, latitude, longitude, light)) {
            stars.put(night.date(), night);
        }
        return new Scores(sunsets, stars);
    }

    /**
     * 날씨를 받는다. 실패하면 한 번 더 시도하고, 그래도 실패하면 503으로 답한다.
     * 일시적인 네트워크 문제는 바로 다시 시도하면 되는 경우가 많다.
     */
    private OpenMeteoForecastResponse fetchWeather(double latitude, double longitude) {
        try {
            return requireWeather(client.getForecast(latitude, longitude));
        } catch (RestClientException first) {
            log.warn("날씨 조회 실패, 한 번 더 시도합니다: {}", first.getMessage());
        }
        try {
            return requireWeather(client.getForecast(latitude, longitude));
        } catch (RestClientException second) {
            log.error("날씨 조회 재시도도 실패했습니다: {}", second.getMessage());
            throw new ApiException(ErrorCode.WEATHER_UNAVAILABLE);
        }
    }

    /** 응답은 왔는데 내용이 비어 있으면 점수를 낼 수 없으므로 실패로 본다. */
    private OpenMeteoForecastResponse requireWeather(OpenMeteoForecastResponse weather) {
        if (weather == null || weather.hourly() == null || weather.daily() == null) {
            throw new RestClientException("Open-Meteo 응답이 비어 있습니다");
        }
        return weather;
    }

    /** 미세먼지는 없어도 점수를 낼 수 있어서, 실패하면 기록만 남기고 null로 넘긴다. */
    private OpenMeteoAirQualityResponse fetchAirQualityOrNull(double latitude, double longitude) {
        try {
            return client.getAirQuality(latitude, longitude);
        } catch (RestClientException e) {
            log.warn("미세먼지 조회 실패, 공기를 빼고 계산합니다: {}", e.getMessage());
            return null;
        }
    }

    /** 소수점 둘째 자리로 반올림한다. 37.5665 → 37.57, 126.978 → 126.98 */
    static double roundCoordinate(double value) {
        return Math.round(value * COORDINATE_SCALE) / COORDINATE_SCALE;
    }

    /** 예보 기준 시각 (정각) */
    private LocalDateTime now() {
        return LocalDateTime.now(SkyCalculator.ZONE).truncatedTo(ChronoUnit.HOURS);
    }

    // ===== 7일 카드 만들기 =====

    private ForecastResponse.SunsetCard toSunsetCard(DailySunsetForecast day) {
        if (day == null) {
            return null;
        }
        SunsetScore score = day.score();
        return new ForecastResponse.SunsetCard(
                score.score(),
                score.grade().label(),
                score.grade().sunsetLine(),
                sunsetReason(score.factors()),
                day.sunset(),
                day.recommendFrom(),
                day.recommendTo(),
                day.note(),
                !score.missing().isEmpty()
        );
    }

    private ForecastResponse.StarCard toStarCard(DailyStarForecast night) {
        if (night == null) {
            return null;
        }
        return new ForecastResponse.StarCard(
                night.score(),
                night.grade().label(),
                night.grade().starLine(),
                starReason(night),
                night.nightStart(),
                night.recommendFrom(),
                night.recommendTo(),
                night.note(),
                night.moonNote(),
                night.lightNote(),
                !starMissing(night).isEmpty()
        );
    }

    // ===== 상세 만들기 =====

    private ForecastDetailResponse.SunsetDetail toSunsetDetail(DailySunsetForecast day) {
        if (day == null) {
            return null;
        }
        SunsetScore score = day.score();
        List<ForecastDetailResponse.FactorLine> lines = new ArrayList<>();
        for (Factor factor : score.factors()) {
            if (factor.points() != 0) { // 0점 요소는 상세 화면에 보여 주지 않는다
                lines.add(new ForecastDetailResponse.FactorLine(factor.name(), factor.points(), factor.reason()));
            }
        }
        return new ForecastDetailResponse.SunsetDetail(
                score.score(),
                score.grade().label(),
                score.grade().sunsetLine(),
                day.sunset(),
                day.recommendFrom(),
                day.recommendTo(),
                day.note(),
                SunsetScoreRules.BASE_SCORE,
                List.copyOf(lines),
                score.missing()
        );
    }

    private ForecastDetailResponse.StarDetail toStarDetail(DailyStarForecast night, double latitude, double longitude) {
        if (night == null) {
            return null;
        }
        List<ForecastDetailResponse.HourBar> bars = new ArrayList<>();
        for (HourlyStarScore hour : night.hours()) {
            bars.add(new ForecastDetailResponse.HourBar(hour.time(), hour.score(), isRecommended(night, hour.time())));
        }
        return new ForecastDetailResponse.StarDetail(
                night.score(),
                night.grade().label(),
                night.grade().starLine(),
                starReason(night),
                night.nightStart(),
                night.recommendFrom(),
                night.recommendTo(),
                night.note(),
                night.moonNote(),
                night.lightNote(),
                sky.moonAt(night.nightStart(), latitude, longitude).illumination(),
                moonSetDuringNight(night, latitude, longitude),
                List.copyOf(bars),
                starMissing(night)
        );
    }

    /**
     * 밤 동안 달이 지는 시각. 밤 시작(천문박명 끝)에 달이 떠 있고 04시 전에 질 때만 돌려준다.
     * 그 밖에는 오늘 밤 별 보기와 상관없는 시각이라 null (moonNote와 같은 기준).
     * 예: 그믐이라 밤에 달이 없으면 null, 반달이 01:10에 지면 01:10
     */
    private LocalDateTime moonSetDuringNight(DailyStarForecast night, double latitude, double longitude) {
        if (!sky.moonAt(night.nightStart(), latitude, longitude).isUp()) {
            return null;
        }
        LocalDateTime moonSet = sky.nextMoonSet(night.nightStart(), latitude, longitude);
        LocalDateTime nightEnd = night.date().plusDays(1).atTime(StarScoreRules.NIGHT_END_HOUR, 0);
        if (moonSet == null || !moonSet.isBefore(nightEnd)) {
            return null;
        }
        return moonSet;
    }

    /** 추천 구간(시작 포함, 끝 미포함) 안의 시간인지 */
    private boolean isRecommended(DailyStarForecast night, LocalDateTime time) {
        return night.recommendFrom() != null
                && !time.isBefore(night.recommendFrom())
                && time.isBefore(night.recommendTo());
    }

    /** 밤 동안 한 시간이라도 빠진 요소 이름 (중복 없이) */
    private List<String> starMissing(DailyStarForecast night) {
        Set<String> missing = new LinkedHashSet<>();
        for (HourlyStarScore hour : night.hours()) {
            missing.addAll(hour.missing());
        }
        return List.copyOf(missing);
    }

    // ===== 이유 한 줄 고르기 (F6 이유 문장 규칙, 기능명세서 구현 기준) =====

    /**
     * 노을 이유 한 줄: 더하거나 뺀 점수의 절댓값이 가장 큰 요소의 문장.
     * 같으면 앞의 요소(①에 가까운 쪽). 문장이 있는 요소가 없으면 null.
     */
    static String sunsetReason(List<Factor> factors) {
        Factor main = null;
        for (Factor factor : factors) {
            if (factor.reason() == null) {
                continue;
            }
            if (main == null || Math.abs(factor.points()) > Math.abs(main.points())) {
                main = factor;
            }
        }
        return main == null ? null : main.reason();
    }

    /**
     * 별 이유 한 줄: 추천 구간 첫 시간(쉬는 날이면 가장 점수가 높은 시간) 기준으로 고른다.
     * 별 요소 점수는 0~100이라, 비중을 곱해 "만점에서 가장 많이 깎인 요소"를 고른다.
     * 예: 구름 50(−25), 공기 75(−5), 달 100(0) → 구름 문장
     * 깎인 요소가 없으면(모두 만점) 비중이 가장 큰 구름의 문장을 쓴다.
     */
    static String starReason(DailyStarForecast night) {
        HourlyStarScore hour = pickReasonHour(night);
        Factor main = null;
        double mainLoss = -1;
        for (Factor factor : hour.factors()) {
            if (factor.reason() == null) {
                continue;
            }
            double loss = (100 - factor.points()) * weightOf(factor.name());
            if (loss > mainLoss) {
                main = factor;
                mainLoss = loss;
            }
        }
        return main == null ? null : main.reason();
    }

    private static HourlyStarScore pickReasonHour(DailyStarForecast night) {
        if (night.recommendFrom() != null) {
            for (HourlyStarScore hour : night.hours()) {
                if (hour.time().equals(night.recommendFrom())) {
                    return hour;
                }
            }
        }
        HourlyStarScore best = night.hours().getFirst();
        for (HourlyStarScore hour : night.hours()) {
            if (hour.score() > best.score()) {
                best = hour;
            }
        }
        return best;
    }

    private static double weightOf(String factorName) {
        return switch (factorName) {
            case StarScoreService.CLOUD_NAME -> StarScoreRules.CLOUD_WEIGHT;
            case StarScoreService.AIR_NAME -> StarScoreRules.AIR_WEIGHT;
            case StarScoreService.MOON_NAME -> StarScoreRules.MOON_WEIGHT;
            default -> 0;
        };
    }
}
