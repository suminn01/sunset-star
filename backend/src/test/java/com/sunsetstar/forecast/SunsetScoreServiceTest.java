package com.sunsetstar.forecast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 노을 점수(기능명세서 F2) 테스트.
 *
 * - 명세서 예시 계산 5개: 명세서 표와 같은 점수·등급이 나와야 한다
 * - 추가 규칙과 구현 기준: 일몰 때 비, PM2.5 반올림, 정보 없음
 * - 추천 시간과 7일치 예보: 가짜 Open-Meteo 응답으로 확인 (인터넷 필요 없음)
 */
class SunsetScoreServiceTest {

    private final SunsetScoreService service = new SunsetScoreService();

    /**
     * 테스트용 일몰 시각 날씨를 만든다. 예시에 나오지 않는 값은 점수에 영향이 없는 값으로 고정한다.
     * (중간 구름 0%, 습도 60%, 시정 20km, 일몰 때 비 없음)
     */
    private SunsetConditions sky(int highCloud, int lowCloud, Integer westSky, double pm25, double rainBefore) {
        return new SunsetConditions(highCloud, 0, lowCloud, westSky, pm25, 60, 20.0, 0.0, rainBefore);
    }

    @Nested
    @DisplayName("기능명세서 F2 예시 계산")
    class SpecExamples {

        @Test
        @DisplayName("맑음 + 공기 좋음 + 서쪽 트임 = 50 + 5 + 10 + 5 = 70 볼 만해요")
        void clearSky() {
            SunsetScore result = service.calculate(sky(0, 0, 10, 10.0, 0.0));

            assertThat(result.score()).isEqualTo(70);
            assertThat(result.grade()).isEqualTo(Grade.WORTH_IT);
        }

        @Test
        @DisplayName("맑음 + 미세먼지 나쁨 + 서쪽 트임 = 50 + 5 + 10 − 10 = 55 그럭저럭")
        void dustyAir() {
            SunsetScore result = service.calculate(sky(0, 0, 10, 50.0, 0.0));

            assertThat(result.score()).isEqualTo(55);
            assertThat(result.grade()).isEqualTo(Grade.SO_SO);
        }

        @Test
        @DisplayName("높은 구름 45% + 낮은 구름 25% + 서쪽 트임 = 50 + 25 − 10 + 10 = 75 볼 만해요")
        void highAndLowCloud() {
            SunsetScore result = service.calculate(sky(45, 25, 10, 25.0, 0.0));

            assertThat(result.score()).isEqualTo(75);
            assertThat(result.grade()).isEqualTo(Grade.WORTH_IT);
        }

        @Test
        @DisplayName("비 갠 뒤 + 높은 구름 55% + 서쪽 트임 + 공기 좋음 = 105 → 100 꼭 보세요")
        void afterRain() {
            SunsetScore result = service.calculate(sky(55, 0, 10, 10.0, 3.0));

            assertThat(result.score()).isEqualTo(100);
            assertThat(result.grade()).isEqualTo(Grade.MUST_SEE);
        }

        @Test
        @DisplayName("꽉 흐림 (위쪽 95%, 낮은 구름 85%, 서쪽 90%) = 50 + 0 − 40 − 20 = −10 → 0 쉬어 가요")
        void overcast() {
            SunsetScore result = service.calculate(sky(95, 85, 90, 25.0, 0.0));

            assertThat(result.score()).isEqualTo(0);
            assertThat(result.grade()).isEqualTo(Grade.REST);
            // 낮은 구름 80% 이상이라 위쪽 구름은 0점
            assertThat(result.factors().getFirst()).isEqualTo(new Factor("위쪽 구름", 0, null));
        }
    }

    @Nested
    @DisplayName("추가 규칙과 구현 기준")
    class Rules {

        @Test
        @DisplayName("일몰 때 비가 오면 다른 요소가 좋아도 최대 20점")
        void rainAtSunset() {
            SunsetConditions rainy = new SunsetConditions(0, 0, 0, 10, 10.0, 60, 20.0, 1.0, 0.0);

            SunsetScore result = service.calculate(rainy);

            assertThat(result.score()).isEqualTo(20);
            // 70점이 20점이 됐으므로 비 요소는 −50으로 기록된다
            assertThat(result.factors().getLast())
                    .isEqualTo(new Factor("비", -50, "일몰 무렵 비 소식이 있어요"));
        }

        @Test
        @DisplayName("PM2.5는 반올림한다: 15.4 → 15 좋음(+5), 15.5 → 16 보통(0)")
        void pm25Rounding() {
            int good = service.calculate(sky(0, 0, null, 15.4, 0.0)).score();
            int normal = service.calculate(sky(0, 0, null, 15.5, 0.0)).score();

            assertThat(good).isEqualTo(60);   // 50 + 5 + 5
            assertThat(normal).isEqualTo(55); // 50 + 5 + 0
        }

        @Test
        @DisplayName("습도 90% 이상이거나 시정 5km 미만이면 −10, 습도 80~89%면 −5")
        void humidityAndVisibility() {
            SunsetConditions foggy = new SunsetConditions(0, 0, 0, null, 25.0, 92, 20.0, 0.0, 0.0);
            SunsetConditions hazy = new SunsetConditions(0, 0, 0, null, 25.0, 60, 3.0, 0.0, 0.0);
            SunsetConditions damp = new SunsetConditions(0, 0, 0, null, 25.0, 85, 20.0, 0.0, 0.0);

            assertThat(service.calculate(foggy).score()).isEqualTo(45); // 50 + 5 − 10
            assertThat(service.calculate(hazy).score()).isEqualTo(45);
            assertThat(service.calculate(damp).score()).isEqualTo(50);  // 50 + 5 − 5
        }

        @Test
        @DisplayName("미세먼지 값이 없으면 공기를 빼고 계산하고, 빠진 정보로 알려 준다")
        void missingAirQuality() {
            SunsetConditions noAir = new SunsetConditions(0, 0, 0, null, null, 60, 20.0, 0.0, 0.0);

            SunsetScore result = service.calculate(noAir);

            assertThat(result.score()).isEqualTo(55); // 50 + 5
            assertThat(result.missing()).containsExactly("공기");
        }

        @Test
        @DisplayName("같은 입력이면 항상 같은 점수가 나온다")
        void sameInputSameScore() {
            SunsetConditions conditions = sky(45, 25, 10, 25.0, 0.0);

            assertThat(service.calculate(conditions)).isEqualTo(service.calculate(conditions));
        }
    }

    @Nested
    @DisplayName("추천 시간")
    class Recommend {

        private final LocalDate date = LocalDate.of(2026, 11, 9);
        private final LocalDateTime sunset = LocalDateTime.of(2026, 11, 9, 18, 20);

        @Test
        @DisplayName("일몰 18:20 → 18:05 ~ 18:40 추천")
        void recommendTime() {
            SunsetScore score = service.calculate(sky(0, 0, 10, 10.0, 0.0)); // 70점

            DailySunsetForecast result = service.recommend(date, sunset, score);

            assertThat(result.recommendFrom()).isEqualTo(LocalDateTime.of(2026, 11, 9, 18, 5));
            assertThat(result.recommendTo()).isEqualTo(LocalDateTime.of(2026, 11, 9, 18, 40));
            assertThat(result.note()).isEqualTo("해가 진 뒤에도 바로 떠나지 마세요");
            assertThat(result.isRestDay()).isFalse();
        }

        @Test
        @DisplayName("30점 미만이면 추천 시간 대신 \"오늘은 쉬어요\"")
        void restDay() {
            SunsetScore score = service.calculate(sky(95, 85, 90, 25.0, 0.0)); // 0점

            DailySunsetForecast result = service.recommend(date, sunset, score);

            assertThat(result.isRestDay()).isTrue();
            assertThat(result.recommendFrom()).isNull();
            assertThat(result.note()).isEqualTo("오늘은 쉬어요");
        }
    }

    @Nested
    @DisplayName("Open-Meteo 응답으로 날짜별 예보 만들기")
    class Forecast {

        /**
         * 이틀치(48시간) 가짜 응답.
         * - 모든 시간: 높은 구름 45%, 낮은 구름 25%, 습도 60%, 시정 20km, PM2.5 10
         * - 첫날: 13~15시에 비 1mm씩, 일몰 17:24 (→ 17:00 값을 쓴다)
         * - 둘째 날: 18시에 비 2mm, 일몰 17:45 (→ 18:00 값을 쓴다)
         */
        private OpenMeteoForecastResponse weather() {
            LocalDateTime start = LocalDateTime.of(2026, 11, 9, 0, 0);
            List<LocalDateTime> time = new ArrayList<>();
            List<Double> rain = new ArrayList<>();
            for (int h = 0; h < 48; h++) {
                time.add(start.plusHours(h));
                boolean rainyHour = (h >= 13 && h <= 15) || h == 24 + 18;
                rain.add(rainyHour ? (h < 24 ? 1.0 : 2.0) : 0.0);
            }
            OpenMeteoForecastResponse.Hourly hourly = new OpenMeteoForecastResponse.Hourly(
                    time,
                    repeat(50, 48),     // 전체 구름
                    repeat(25, 48),     // 낮은 구름
                    repeat(0, 48),      // 중간 구름
                    repeat(45, 48),     // 높은 구름
                    repeat(60, 48),     // 습도
                    repeat(20000.0, 48), // 시정 (m)
                    rain
            );
            OpenMeteoForecastResponse.Daily daily = new OpenMeteoForecastResponse.Daily(
                    List.of(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10)),
                    null,
                    List.of(LocalDateTime.of(2026, 11, 9, 17, 24), LocalDateTime.of(2026, 11, 10, 17, 45))
            );
            return new OpenMeteoForecastResponse(37.5665, 126.978, 38.0, hourly, daily);
        }

        private OpenMeteoAirQualityResponse airQuality() {
            return new OpenMeteoAirQualityResponse(37.5665, 126.978,
                    new OpenMeteoAirQualityResponse.Hourly(weather().hourly().time(), repeat(10.0, 48), repeat(20.0, 48)));
        }

        private <T> List<T> repeat(T value, int count) {
            return new ArrayList<>(java.util.Collections.nCopies(count, value));
        }

        @Test
        @DisplayName("날짜마다 하나씩 예보가 나온다")
        void oneForecastPerDay() {
            List<DailySunsetForecast> days = service.forecast(weather(), airQuality());

            assertThat(days).hasSize(2);
            assertThat(days.get(0).date()).isEqualTo(LocalDate.of(2026, 11, 9));
        }

        @Test
        @DisplayName("일몰 전 6시간 안에 비가 오고 그쳤으면 +15 (50 + 25 − 10 + 5 + 15 = 85)")
        void afterRainDay() {
            DailySunsetForecast first = service.forecast(weather(), airQuality()).get(0);

            assertThat(first.score().score()).isEqualTo(85);
            assertThat(first.score().factors()).contains(
                    new Factor("비", 15, "비 갠 뒤라 공기가 깨끗해요. 노을이 좋은 경우가 많아요"));
            assertThat(first.recommendFrom()).isEqualTo(LocalDateTime.of(2026, 11, 9, 17, 9));
            assertThat(first.recommendTo()).isEqualTo(LocalDateTime.of(2026, 11, 9, 17, 44));
        }

        @Test
        @DisplayName("일몰(17:45 → 18:00 값)에 비가 오면 20점, 쉬는 날")
        void rainAtSunsetDay() {
            DailySunsetForecast second = service.forecast(weather(), airQuality()).get(1);

            assertThat(second.score().score()).isEqualTo(20);
            assertThat(second.isRestDay()).isTrue();
        }

        @Test
        @DisplayName("미세먼지 응답이 없으면 공기를 빼고 계산한다")
        void withoutAirQuality() {
            DailySunsetForecast first = service.forecast(weather(), null).get(0);

            assertThat(first.score().score()).isEqualTo(80); // 85에서 공기 +5가 빠짐
            assertThat(first.score().missing()).containsExactly("공기");
        }
    }
}
