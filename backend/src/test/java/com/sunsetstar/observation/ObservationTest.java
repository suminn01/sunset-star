package com.sunsetstar.observation;

import com.sunsetstar.forecast.SkyType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 적중 판정 테스트 (기능명세서 F7: 60점 이상 + GOOD, 60점 미만 + BAD면 적중, SOSO는 빼기).
 */
class ObservationTest {

    private Observation observation(int predictedScore, Rating rating) {
        return new Observation(null, LocalDate.of(2026, 11, 23), SkyType.SUNSET,
                new BigDecimal("37.5665"), new BigDecimal("126.978"),
                predictedScore, null, rating, null, null, null, false, false);
    }

    @ParameterizedTest(name = "{0}점 + {1} → {2}")
    @CsvSource({
            "60, GOOD, true",   // 경계: 60점은 "좋다"고 예측한 것
            "85, GOOD, true",
            "59, GOOD, false",
            "59, BAD, true",
            "20, BAD, true",
            "60, BAD, false",
    })
    void 적중_판정(int score, Rating rating, boolean hit) {
        assertThat(observation(score, rating).isHit()).isEqualTo(hit);
    }

    @ParameterizedTest
    @CsvSource({"90", "59", "10"})
    void SOSO는_판정하지_않는다(int score) {
        assertThat(observation(score, Rating.SOSO).isHit()).isNull();
    }
}
