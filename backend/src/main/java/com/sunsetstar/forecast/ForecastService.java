package com.sunsetstar.forecast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 7일 예보를 만든다 (기능명세서 F6). Controller가 부르는 곳이다.
 *
 * 순서
 * 1. OpenMeteoClient로 날씨와 미세먼지를 받는다
 * 2. SunsetScoreService, StarScoreService로 날짜마다 노을·별 점수를 계산한다
 * 3. 날짜별 카드(ForecastResponse)로 합친다: 등급 이름, 이유 한 줄, 4일째부터 "참고용"
 *
 * 미세먼지 API만 실패하면 공기를 빼고 계산한다(F8 "일부 데이터 없음").
 * 날씨 API가 실패하면 점수를 낼 수 없으므로 예외를 그대로 던진다(에러 응답은 다음 작업에서 만든다).
 */
@Service
public class ForecastService {

    private static final Logger log = LoggerFactory.getLogger(ForecastService.class);

    /** 4일째(index 3)부터 "참고용" */
    static final int REFERENCE_FROM_DAY_INDEX = 3;

    private final OpenMeteoClient client;
    private final SunsetScoreService sunsetScoreService;
    private final StarScoreService starScoreService;

    public ForecastService(OpenMeteoClient client,
                           SunsetScoreService sunsetScoreService,
                           StarScoreService starScoreService) {
        this.client = client;
        this.sunsetScoreService = sunsetScoreService;
        this.starScoreService = starScoreService;
    }

    /** 좌표와 빛 공해 단계로 7일 예보를 만든다. */
    public ForecastResponse forecast(double latitude, double longitude, LightPollution light) {
        OpenMeteoForecastResponse weather = client.getForecast(latitude, longitude);
        OpenMeteoAirQualityResponse airQuality = fetchAirQualityOrNull(latitude, longitude);
        LocalDateTime basedAt = LocalDateTime.now(SkyCalculator.ZONE).truncatedTo(ChronoUnit.HOURS);
        return toResponse(weather, airQuality, latitude, longitude, light, basedAt);
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

    /**
     * Open-Meteo 응답으로 화면용 응답을 만든다. 외부 호출이 없어서 테스트에서 바로 부를 수 있다.
     */
    ForecastResponse toResponse(OpenMeteoForecastResponse weather, OpenMeteoAirQualityResponse airQuality,
                                double latitude, double longitude, LightPollution light,
                                LocalDateTime basedAt) {
        Map<LocalDate, DailySunsetForecast> sunsets = new HashMap<>();
        for (DailySunsetForecast day : sunsetScoreService.forecast(weather, airQuality)) {
            sunsets.put(day.date(), day);
        }
        Map<LocalDate, DailyStarForecast> stars = new HashMap<>();
        for (DailyStarForecast night : starScoreService.forecast(weather, airQuality, latitude, longitude, light)) {
            stars.put(night.date(), night);
        }

        List<ForecastResponse.DayCard> days = new ArrayList<>();
        List<LocalDate> dates = weather.daily().time();
        for (int i = 0; i < dates.size(); i++) {
            LocalDate date = dates.get(i);
            days.add(new ForecastResponse.DayCard(
                    date,
                    i >= REFERENCE_FROM_DAY_INDEX,
                    toSunsetCard(sunsets.get(date)),
                    toStarCard(stars.get(date))
            ));
        }
        return new ForecastResponse(latitude, longitude, light, light.label(), basedAt, List.copyOf(days));
    }

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
        boolean partial = night.hours().stream().anyMatch(hour -> !hour.missing().isEmpty());
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
                partial
        );
    }

    // ===== 이유 한 줄 고르기 (F6 이유 문장 규칙) =====

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
