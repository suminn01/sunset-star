package com.sunsetstar.forecast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 별 점수(기능명세서 F4) 테스트.
 *
 * - 명세서 예시 계산 6개
 * - 명세서 완료 기준: 보름달·그믐 차이, 빛 공해 계수, 추천 구간 세 가지 경우
 * - 추가 규칙: 구름 상한, 비, 습기, 정보 없음
 * - 7일치 예보 만들기: 해·달 계산을 가짜(FakeSky)로 바꿔 끼워서 확인 (인터넷 필요 없음)
 */
class StarScoreServiceTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 11, 9, 22, 0);

    // 달 상태 예시
    private static final SkyCalculator.Moon NEW_MOON = new SkyCalculator.Moon(0.02, 30);  // 그믐, 떠 있음
    private static final SkyCalculator.Moon MOON_DOWN = new SkyCalculator.Moon(0.9, -10); // 진 시간
    private static final SkyCalculator.Moon FULL_MOON = new SkyCalculator.Moon(1.0, 30);  // 보름달, 떠 있음
    private static final SkyCalculator.Moon HALF_MOON = new SkyCalculator.Moon(0.5, 30);  // 반달, 떠 있음

    private final StarScoreService service = new StarScoreService(new SkyCalculator());

    /** 한 시간 점수. 습도 60%, 시정 20km, 비 없음으로 고정한다. */
    private int score(int cloud, double pm25, SkyCalculator.Moon moon, LightPollution light) {
        StarHourConditions c = new StarHourConditions(TIME, cloud, pm25, 60, 20.0, 0.0, moon);
        return service.calculateHour(c, light).score();
    }

    @Nested
    @DisplayName("기능명세서 F4 예시 계산")
    class SpecExamples {

        @Test
        @DisplayName("시골, 맑음, 공기 좋음, 그믐: (50 + 20 + 30) × 1.0 = 100")
        void ruralBest() {
            assertThat(score(0, 10.0, NEW_MOON, LightPollution.RURAL)).isEqualTo(100);
        }

        @Test
        @DisplayName("교외, 맑음, 미세먼지 보통, 달 진 시간: (50 + 15 + 30) × 0.9 = 85.5 → 86")
        void suburbMoonDown() {
            assertThat(score(0, 25.0, MOON_DOWN, LightPollution.SUBURB)).isEqualTo(86);
        }

        @Test
        @DisplayName("도심, 맑음, 공기 좋음, 그믐: (50 + 20 + 30) × 0.75 = 75")
        void cityBest() {
            assertThat(score(0, 10.0, NEW_MOON, LightPollution.CITY)).isEqualTo(75);
        }

        @Test
        @DisplayName("교외, 맑음, 공기 좋음, 보름달이 떠 있음: (50 + 20 + 3) × 0.9 = 65.7 → 66")
        void suburbFullMoon() {
            assertThat(score(0, 10.0, FULL_MOON, LightPollution.SUBURB)).isEqualTo(66);
        }

        @Test
        @DisplayName("교외, 구름 40%, 미세먼지 보통, 반달: (25 + 15 + 16.5) × 0.9 = 50.85 → 51")
        void suburbHalfMoon() {
            assertThat(score(40, 25.0, HALF_MOON, LightPollution.SUBURB)).isEqualTo(51);
        }

        @Test
        @DisplayName("교외, 구름 95% (꽉 흐림): 계산값 40.5 → 구름 상한 규칙으로 0")
        void overcast() {
            assertThat(score(95, 25.0, HALF_MOON, LightPollution.SUBURB)).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("추가 규칙과 완료 기준")
    class Rules {

        @Test
        @DisplayName("보름달 밤과 그믐 밤은 같은 날씨에서도 점수 차이가 난다 (90 vs 66)")
        void fullMoonVsNewMoon() {
            int newMoon = score(0, 10.0, NEW_MOON, LightPollution.SUBURB);
            int fullMoon = score(0, 10.0, FULL_MOON, LightPollution.SUBURB);

            assertThat(newMoon).isEqualTo(90);
            assertThat(fullMoon).isEqualTo(66);
        }

        @Test
        @DisplayName("빛 공해 단계를 바꾸면 같은 날씨에서도 계수만큼 바뀐다 (시골 100, 교외 90, 도심 75)")
        void lightPollutionFactor() {
            assertThat(score(0, 10.0, NEW_MOON, LightPollution.RURAL)).isEqualTo(100);
            assertThat(score(0, 10.0, NEW_MOON, LightPollution.SUBURB)).isEqualTo(90);
            assertThat(score(0, 10.0, NEW_MOON, LightPollution.CITY)).isEqualTo(75);
        }

        @Test
        @DisplayName("구름 71~90%면 최대 30점 (계산값 55 → 30)")
        void heavyCloudCap() {
            // 구름 80% → 10점: (5 + 20 + 30) × 1.0 = 55
            assertThat(score(80, 10.0, MOON_DOWN, LightPollution.RURAL)).isEqualTo(30);
        }

        @Test
        @DisplayName("비가 오는 시간은 0점")
        void rain() {
            StarHourConditions rainy = new StarHourConditions(TIME, 0, 10.0, 60, 20.0, 0.5, MOON_DOWN);

            assertThat(service.calculateHour(rainy, LightPollution.RURAL).score()).isEqualTo(0);
        }

        @Test
        @DisplayName("습도 90% 이상이면 공기 점수에서 30점을 빼고 습기 문장을 쓴다 (100 → 70)")
        void haze() {
            StarHourConditions humid = new StarHourConditions(TIME, 0, 10.0, 95, 20.0, 0.0, MOON_DOWN);

            HourlyStarScore result = service.calculateHour(humid, LightPollution.RURAL);

            assertThat(result.score()).isEqualTo(94); // 50 + 70×0.2 + 30
            assertThat(result.factors()).contains(new Factor("공기", 70, "습기 때문에 하늘이 뿌얘요"));
        }

        @Test
        @DisplayName("미세먼지 값이 없으면 공기를 빼고 남은 비중으로 다시 나눈다")
        void missingAir() {
            StarHourConditions noAir = new StarHourConditions(TIME, 0, null, 60, 20.0, 0.0, MOON_DOWN);

            HourlyStarScore result = service.calculateHour(noAir, LightPollution.RURAL);

            assertThat(result.score()).isEqualTo(100); // (50 + 30) ÷ 0.8
            assertThat(result.missing()).containsExactly("공기");
        }
    }

    @Nested
    @DisplayName("대표 점수와 추천 구간 (세 가지 경우)")
    class Summarize {

        private final LocalDate date = LocalDate.of(2026, 11, 9);
        private final LocalDateTime nightStart = LocalDateTime.of(2026, 11, 9, 18, 56);

        /** 23시부터 한 시간씩 주어진 점수로 시간별 점수 목록을 만든다. */
        private List<HourlyStarScore> hours(int... scores) {
            List<HourlyStarScore> list = new ArrayList<>();
            for (int i = 0; i < scores.length; i++) {
                list.add(new HourlyStarScore(date.atTime(23, 0).plusHours(i), scores[i], List.of(), List.of()));
            }
            return list;
        }

        @Test
        @DisplayName("연속 구간: 23시 70, 00시 78, 01시 80, 02시 76, 03시 60 → 대표 80, 추천 00:00 ~ 03:00")
        void continuousRange() {
            DailyStarForecast result = service.summarize(date, nightStart, hours(70, 78, 80, 76, 60),
                    LightPollution.SUBURB, null);

            assertThat(result.score()).isEqualTo(80);
            assertThat(result.grade()).isEqualTo(Grade.WORTH_IT);
            assertThat(result.recommendFrom()).isEqualTo(LocalDateTime.of(2026, 11, 10, 0, 0));
            assertThat(result.recommendTo()).isEqualTo(LocalDateTime.of(2026, 11, 10, 3, 0));
        }

        @Test
        @DisplayName("1시간: 23시 40, 00시 60, 01시 50 → 추천 00:00 ~ 01:00")
        void oneHour() {
            DailyStarForecast result = service.summarize(date, nightStart, hours(40, 60, 50),
                    LightPollution.SUBURB, null);

            assertThat(result.recommendFrom()).isEqualTo(LocalDateTime.of(2026, 11, 10, 0, 0));
            assertThat(result.recommendTo()).isEqualTo(LocalDateTime.of(2026, 11, 10, 1, 0));
        }

        @Test
        @DisplayName("쉬어요: 가장 높은 점수가 30점 미만이면 추천 구간 대신 쉬는 날 안내")
        void restDay() {
            DailyStarForecast result = service.summarize(date, nightStart, hours(20, 25, 10),
                    LightPollution.RURAL, null);

            assertThat(result.isRestDay()).isTrue();
            assertThat(result.note()).isEqualTo("오늘 밤은 쉬어 가는 날이에요");
            assertThat(result.lightNote()).isNull(); // 쉬는 날에는 시골 문장도 숨긴다
        }

        @Test
        @DisplayName("도심 문장은 70점 이상일 때만 보여 준다")
        void cityNote() {
            DailyStarForecast good = service.summarize(date, nightStart, hours(72), LightPollution.CITY, null);
            DailyStarForecast soso = service.summarize(date, nightStart, hours(60), LightPollution.CITY, null);

            assertThat(good.lightNote()).startsWith("도심에서 이 정도면 아주 좋은 밤이에요");
            assertThat(soso.lightNote()).isNull();
        }
    }

    @Nested
    @DisplayName("Open-Meteo 응답으로 밤 예보 만들기")
    class Forecast {

        /**
         * 해·달 계산을 대신하는 가짜. 매일 천문박명 끝 18:56, 반달이 떠 있다가 다음 날 01:10에 진다.
         * SkyCalculator를 상속해서 메서드만 바꿔 끼운다.
         */
        private static class FakeSky extends SkyCalculator {
            @Override
            public LocalDateTime astronomicalDusk(LocalDate date, double latitude, double longitude) {
                return date.atTime(18, 56);
            }

            @Override
            public Moon moonAt(LocalDateTime time, double latitude, double longitude) {
                boolean up = time.getHour() >= 12 || time.getHour() < 1
                        || (time.getHour() == 1 && time.getMinute() < 10);
                return new Moon(0.5, up ? 30 : -10);
            }

            @Override
            public LocalDateTime nextMoonSet(LocalDateTime from, double latitude, double longitude) {
                return from.toLocalDate().plusDays(1).atTime(1, 10);
            }
        }

        private final StarScoreService fakeSkyService = new StarScoreService(new FakeSky());

        /** 이틀치(48시간) 가짜 응답: 맑음(구름 0%), 습도 60%, 시정 20km, 비 없음, PM2.5 10 */
        private List<DailyStarForecast> forecast() {
            LocalDateTime start = LocalDateTime.of(2026, 11, 9, 0, 0);
            List<LocalDateTime> time = new ArrayList<>();
            for (int h = 0; h < 48; h++) {
                time.add(start.plusHours(h));
            }
            OpenMeteoForecastResponse.Hourly hourly = new OpenMeteoForecastResponse.Hourly(
                    time, repeat(0), null, null, null, repeat(60), repeat(20000.0), repeat(0.0));
            OpenMeteoForecastResponse.Daily daily = new OpenMeteoForecastResponse.Daily(
                    List.of(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10)), null, null);
            OpenMeteoAirQualityResponse air = new OpenMeteoAirQualityResponse(37.5665, 126.978,
                    new OpenMeteoAirQualityResponse.Hourly(time, repeat(10.0), null));

            return fakeSkyService.forecast(new OpenMeteoForecastResponse(37.5665, 126.978, 38.0, hourly, daily),
                    air, 37.5665, 126.978, LightPollution.SUBURB);
        }

        private <T> List<T> repeat(T value) {
            return new ArrayList<>(Collections.nCopies(48, value));
        }

        @Test
        @DisplayName("천문박명 끝 18:56 → 19:00부터 03:00까지 정각마다 9칸")
        void nightHours() {
            DailyStarForecast first = forecast().getFirst();

            assertThat(first.hours()).hasSize(9);
            assertThat(first.hours().getFirst().time()).isEqualTo(LocalDateTime.of(2026, 11, 9, 19, 0));
            assertThat(first.hours().getLast().time()).isEqualTo(LocalDateTime.of(2026, 11, 10, 3, 0));
        }

        @Test
        @DisplayName("반달이 진 뒤(02시~)가 가장 좋다: 대표 90, 추천 02:00 ~ 04:00")
        void bestAfterMoonSet() {
            DailyStarForecast first = forecast().getFirst();

            // 달이 떠 있는 시간: (50 + 20 + 16.5) × 0.9 = 77.85 → 78, 진 뒤: (50 + 20 + 30) × 0.9 = 90
            assertThat(first.hours().getFirst().score()).isEqualTo(78);
            assertThat(first.score()).isEqualTo(90);
            assertThat(first.recommendFrom()).isEqualTo(LocalDateTime.of(2026, 11, 10, 2, 0));
            assertThat(first.recommendTo()).isEqualTo(LocalDateTime.of(2026, 11, 10, 4, 0));
        }

        @Test
        @DisplayName("초저녁에 떠 있던 달이 밤 중간에 지면 \"달이 01:10에 지면 더 좋아요\"")
        void moonNote() {
            assertThat(forecast().getFirst().moonNote()).isEqualTo("달이 01:10에 지면 더 좋아요");
        }

        @Test
        @DisplayName("예보 데이터가 끝나는 마지막 날 밤은 있는 시간까지만 계산한다 (19~23시, 5칸)")
        void lastNightIsCut() {
            DailyStarForecast last = forecast().getLast();

            assertThat(last.hours()).hasSize(5);
        }
    }
}
