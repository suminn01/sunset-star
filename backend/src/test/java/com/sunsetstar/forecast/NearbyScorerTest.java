package com.sunsetstar.forecast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 근처 장소 점수 계산(F5) 테스트. 가짜 날씨 응답과 가짜 해·달(FakeSky)로 인터넷 없이 확인한다.
 *
 * 기본 날씨(노을): 높은 구름 45%, 낮은 구름 0%, PM2.5 10, 습도 60%, 비 없음
 *   → 50 + 25(위쪽 구름) + 5(공기) = 80점
 * 기본 날씨(별): 구름 0%, PM2.5 10, 달 없음 → 교외 90 / 도심 75 / 시골 100
 */
class NearbyScorerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);
    private static final double LAT = 37.57;
    private static final double LON = 126.98;

    /** 해·달 계산을 대신하는 가짜: 천문박명 끝 18:56, 달은 항상 지평선 아래 */
    private static class FakeSky extends SkyCalculator {
        @Override
        public LocalDateTime astronomicalDusk(LocalDate date, double latitude, double longitude) {
            return date.atTime(18, 56);
        }

        @Override
        public Moon moonAt(LocalDateTime time, double latitude, double longitude) {
            return new Moon(0.0, -10);
        }

        @Override
        public LocalDateTime nextMoonSet(LocalDateTime from, double latitude, double longitude) {
            return null;
        }
    }

    private final NearbyScorer scorer =
            new NearbyScorer(new SunsetScoreService(), new StarScoreService(new FakeSky()));

    /** 이틀치(48시간) 가짜 날씨. 고도만 바꿔 쓴다 */
    private static OpenMeteoForecastResponse weather(double elevation) {
        List<LocalDateTime> time = new ArrayList<>();
        for (int h = 0; h < 48; h++) {
            time.add(DAY.atStartOfDay().plusHours(h));
        }
        OpenMeteoForecastResponse.Hourly hourly = new OpenMeteoForecastResponse.Hourly(
                time, repeat(0), repeat(0), repeat(0), repeat(45), repeat(60), repeat(20000.0), repeat(0.0));
        OpenMeteoForecastResponse.Daily daily = new OpenMeteoForecastResponse.Daily(
                List.of(DAY, DAY.plusDays(1)), null, List.of(DAY.atTime(17, 27), DAY.plusDays(1).atTime(17, 26)));
        return new OpenMeteoForecastResponse(LAT, LON, elevation, hourly, daily);
    }

    private static OpenMeteoAirQualityResponse air() {
        List<LocalDateTime> time = weather(0).hourly().time();
        return new OpenMeteoAirQualityResponse(LAT, LON,
                new OpenMeteoAirQualityResponse.Hourly(time, repeat(10.0), repeat(20.0)));
    }

    private static <T> List<T> repeat(T value) {
        return new ArrayList<>(Collections.nCopies(48, value));
    }

    private static NearbyCandidate candidate(String name, String keyword) {
        return new NearbyCandidate("id-" + name, name, keyword, new Coordinate(37.55, 126.95), 3.0,
                "주소", "https://place.map.kakao.com/1");
    }

    @Nested
    @DisplayName("노을")
    class Sunset {

        private NearbyScorer.Scores score(LightPollution light, List<NearbyCandidate> candidates,
                                          OpenMeteoForecastResponse... weathers) {
            return scorer.scoreSunset(DAY, light, weather(38.0), air(), Map.of(),
                    candidates, Arrays.asList(weathers), Collections.nCopies(candidates.size(), air()));
        }

        @Test
        @DisplayName("현재 위치(고도 38m) 80점, 같은 날씨의 200m 높은 전망대는 +15 → 95점")
        void higherOpenView() {
            NearbyCandidate tower = candidate("하늘 전망대", "전망대");

            NearbyScorer.Scores scores = score(LightPollution.SUBURB, List.of(tower), weather(238.0));

            assertThat(scores.current().score()).isEqualTo(80);
            ScoredPlace place = scores.candidates().getFirst();
            assertThat(place.weatherScore()).isEqualTo(80);
            assertThat(place.correction()).isEqualTo(15); // 높은 곳 +10, 트인 곳 +5
            assertThat(place.score()).isEqualTo(95);
            assertThat(place.reasons()).containsExactly(NearbyRules.HIGHER_REASON, NearbyRules.OPEN_VIEW_REASON);
            assertThat(place.estimated()).isTrue();
        }

        @Test
        @DisplayName("60m 높은 그냥 공원은 높은 곳 +5만 받고, 짐작한 값이 없어서 \"추정\"이 아니다")
        void slightlyHigherPark() {
            NearbyScorer.Scores scores = score(LightPollution.SUBURB,
                    List.of(candidate("동네 공원", "공원")), weather(98.0));

            ScoredPlace place = scores.candidates().getFirst();
            assertThat(place.correction()).isEqualTo(5);
            assertThat(place.score()).isEqualTo(85);
            assertThat(place.estimated()).isFalse();
        }

        @Test
        @DisplayName("현재 위치가 도심이면 현재 위치 점수 −5 (80 → 75)")
        void cityCurrentLocation() {
            NearbyScorer.Scores scores = score(LightPollution.CITY, List.of());

            assertThat(scores.current().weatherScore()).isEqualTo(80);
            assertThat(scores.current().score()).isEqualTo(75);
        }

        @Test
        @DisplayName("날씨를 못 받은 후보는 빼고 나머지만 점수를 매긴다")
        void missingWeather() {
            NearbyScorer.Scores scores = score(LightPollution.SUBURB,
                    List.of(candidate("A", "공원"), candidate("B", "공원")), null, weather(38.0));

            assertThat(scores.candidates()).extracting(p -> p.candidate().name()).containsExactly("B");
        }
    }

    @Nested
    @DisplayName("별")
    class Star {

        private NearbyScorer.Scores score(LightPollution light, NearbyCandidate candidate,
                                          OpenMeteoForecastResponse weather) {
            return scorer.scoreStar(DAY, light, LAT, LON, weather(38.0), air(),
                    List.of(candidate), List.of(weather), List.of(air()));
        }

        @Test
        @DisplayName("도심 사용자(75점)에게 천문대는 시골로 계산돼 100점, 불빛이 적다는 이유가 붙는다")
        void observatoryFromCity() {
            NearbyScorer.Scores scores = score(LightPollution.CITY, candidate("별빛 천문대", "천문대"), weather(200.0));

            assertThat(scores.current().score()).isEqualTo(75);
            ScoredPlace place = scores.candidates().getFirst();
            assertThat(place.weatherScore()).isEqualTo(100);
            assertThat(place.reasons()).containsExactly(NearbyRules.DARK_REASON);
            assertThat(place.estimated()).isTrue();
        }

        @Test
        @DisplayName("교외 사용자(90점)에게 캠핑장은 같은 교외라 90점, 해발 600m면 +5 → 95점")
        void highCampingSite() {
            NearbyScorer.Scores scores = score(LightPollution.SUBURB, candidate("산속 캠핑장", "캠핑장"), weather(600.0));

            ScoredPlace place = scores.candidates().getFirst();
            assertThat(place.weatherScore()).isEqualTo(90);
            assertThat(place.correction()).isEqualTo(5);
            assertThat(place.score()).isEqualTo(95);
            assertThat(place.reasons()).containsExactly(NearbyRules.HIGH_ALTITUDE_REASON);
        }
    }
}
