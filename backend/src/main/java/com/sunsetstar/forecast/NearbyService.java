package com.sunsetstar.forecast;

import com.sunsetstar.global.ApiException;
import com.sunsetstar.global.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.sunsetstar.forecast.NearbyRules.*;

/**
 * 근처 관측 장소 추천 (기능명세서 F5). NearbyController가 부르는 곳이다.
 *
 * 1. 현재 위치 날씨 (ForecastService와 같은 방식, 실패하면 재시도 후 503)
 * 2. 후보 찾기 (NearbyCandidateService, 카카오, 하루 캐시)
 * 3. 후보 날씨·미세먼지 (Open-Meteo 다중 좌표 요청 1번씩)
 * 4. 점수 (NearbyScorer)
 * 5. 추천: 현재 위치보다 10점 이상 높은 곳을 점수 차이가 큰 순으로 최대 3곳 (같으면 가까운 곳)
 *
 * 카카오나 후보 날씨가 실패해도 현재 위치 점수는 정상으로 보여 준다 (F5 완료 기준, F8).
 */
@Service
public class NearbyService {

    private static final Logger log = LoggerFactory.getLogger(NearbyService.class);

    /** 카카오에서 후보를 못 찾았거나 후보 날씨를 못 받았을 때 (기능명세서 F8) */
    static final String NO_PLACES_MESSAGE = "지금은 근처 장소를 찾지 못했어요";

    private final ForecastService forecastService;
    private final NearbyCandidateService candidateService;
    private final NearbyScorer scorer;
    private final WestSkyService westSkyService;
    private final OpenMeteoClient client;

    public NearbyService(ForecastService forecastService,
                         NearbyCandidateService candidateService,
                         NearbyScorer scorer,
                         WestSkyService westSkyService,
                         OpenMeteoClient client) {
        this.forecastService = forecastService;
        this.candidateService = candidateService;
        this.scorer = scorer;
        this.westSkyService = westSkyService;
        this.client = client;
    }

    /**
     * 근처 관측 장소 추천.
     *
     * @throws ApiException 400 INVALID_PARAMETER: 예보 범위(오늘부터 7일) 밖의 날짜
     * @throws ApiException 503 WEATHER_UNAVAILABLE: 현재 위치 날씨를 못 받았을 때
     */
    public NearbyResponse nearby(double latitude, double longitude, LightPollution light,
                                 SkyType type, LocalDate date) {
        latitude = ForecastService.roundCoordinate(latitude);
        longitude = ForecastService.roundCoordinate(longitude);

        // 1. 현재 위치 날씨
        OpenMeteoForecastResponse weather = forecastService.fetchWeather(latitude, longitude);
        if (!weather.daily().time().contains(date)) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER,
                    "예보가 있는 날짜가 아니에요. 오늘부터 7일 안의 날짜를 골라 주세요: " + date);
        }
        OpenMeteoAirQualityResponse air = forecastService.fetchAirQualityOrNull(latitude, longitude);

        // 2. 후보 찾기
        List<NearbyCandidate> candidates = candidateService.findCandidates(type, latitude, longitude);

        // 3. 후보 날씨·미세먼지 (실패하면 후보 없이 현재 위치만)
        List<Coordinate> points = candidates.stream().map(NearbyCandidate::coordinate).toList();
        List<OpenMeteoForecastResponse> candidateWeathers = List.of();
        List<OpenMeteoAirQualityResponse> candidateAirs = null;
        if (!points.isEmpty()) {
            try {
                candidateWeathers = client.getForecasts(points);
            } catch (RestClientException e) {
                log.warn("후보 장소 날씨 조회 실패, 현재 위치만 보여 줍니다: {}", e.getMessage());
                candidates = List.of();
            }
            try {
                candidateAirs = client.getAirQualities(points);
            } catch (RestClientException e) {
                log.warn("후보 장소 미세먼지 조회 실패, 공기를 빼고 계산합니다: {}", e.getMessage());
            }
        }

        // 4. 점수
        NearbyScorer.Scores scores;
        if (type == SkyType.SUNSET) {
            Map<LocalDate, WestSky> westSkies = westSkyService.check(weather, latitude, longitude);
            scores = scorer.scoreSunset(date, light, weather, air, westSkies,
                    candidates, candidateWeathers, candidateAirs);
        } else {
            scores = scorer.scoreStar(date, light, latitude, longitude, weather, air,
                    candidates, candidateWeathers, candidateAirs);
        }
        if (scores == null) {
            throw new ApiException(ErrorCode.WEATHER_UNAVAILABLE);
        }

        // 5. 추천
        return recommend(type, date, scores);
    }

    /**
     * 점수로 추천 장소와 안내 문구를 정한다. 외부 호출이 없어서 테스트에서 바로 부를 수 있다.
     *
     * - 현재 위치보다 10점 이상 높은 후보를, 점수 차이가 큰 순(같으면 가까운 순)으로 최대 3곳
     * - 후보가 하나도 없으면 "지금은 근처 장소를 찾지 못했어요"
     * - 현재 위치가 40점 미만이고 추천할 곳도 모두 40점 미만이면 추천 없이 "오늘은 근처 어디도 쉬는 날이에요"
     * - 추천할 곳이 없으면 "지금 있는 곳도 충분히 좋아요"
     */
    static NearbyResponse recommend(SkyType type, LocalDate date, NearbyScorer.Scores scores) {
        ScoredPlace current = scores.current();
        NearbyResponse.Current currentView =
                new NearbyResponse.Current(current.score(), Grade.of(current.score()).label());

        if (scores.candidates().isEmpty()) {
            return new NearbyResponse(type, type.label(), date, currentView, List.of(), NO_PLACES_MESSAGE);
        }

        List<ScoredPlace> better = scores.candidates().stream()
                .filter(place -> place.score() - current.score() >= MIN_GAIN)
                .sorted(Comparator
                        .comparingInt((ScoredPlace place) -> place.score() - current.score()).reversed()
                        .thenComparingDouble(place -> place.candidate().distanceKm()))
                .limit(MAX_RECOMMENDATIONS)
                .toList();

        boolean allPoor = current.score() < ALL_POOR_BELOW
                && better.stream().allMatch(place -> place.score() < ALL_POOR_BELOW);
        if (allPoor) {
            return new NearbyResponse(type, type.label(), date, currentView, List.of(), ALL_POOR_MESSAGE);
        }
        if (better.isEmpty()) {
            return new NearbyResponse(type, type.label(), date, currentView, List.of(), NO_BETTER_MESSAGE);
        }

        List<NearbyResponse.Recommendation> recommendations = better.stream()
                .map(place -> toRecommendation(place, current.score()))
                .toList();
        return new NearbyResponse(type, type.label(), date, currentView, recommendations, null);
    }

    private static NearbyResponse.Recommendation toRecommendation(ScoredPlace place, int currentScore) {
        NearbyCandidate candidate = place.candidate();
        double distanceKm = Math.round(candidate.distanceKm() * 10) / 10.0;
        return new NearbyResponse.Recommendation(
                candidate.name(),
                candidate.address(),
                candidate.coordinate().latitude(),
                candidate.coordinate().longitude(),
                distanceKm,
                place.score(),
                Grade.of(place.score()).label(),
                place.score() - currentScore,
                place.reasons(),
                place.estimated(),
                candidate.placeUrl()
        );
    }
}
