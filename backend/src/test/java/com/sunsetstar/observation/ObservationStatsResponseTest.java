package com.sunsetstar.observation;

import com.sunsetstar.forecast.SkyType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관측 통계 계산 테스트 (기능명세서 F7 완료 기준: "적중률이 기록 수와 함께 표시된다").
 */
class ObservationStatsResponseTest {

    private Observation record(int score, Rating rating, boolean fromRecommendation, boolean fromNotification) {
        return new Observation(null, LocalDate.of(2026, 11, 23), SkyType.SUNSET,
                new BigDecimal("37.5665"), new BigDecimal("126.978"),
                score, null, rating, null, null, null, fromRecommendation, fromNotification);
    }

    @Test
    void 기록_3개로_적중률을_계산한다() {
        // 개발 순서 9단계 확인 기준: "기록 3개 넣고 적중률이 맞게 나옴"
        List<Observation> records = List.of(
                record(80, Rating.GOOD, false, false),  // 적중
                record(30, Rating.BAD, false, false),   // 적중
                record(75, Rating.BAD, false, false));  // 빗나감

        ObservationStatsResponse stats = ObservationStatsResponse.from(records);

        assertThat(stats.totalCount()).isEqualTo(3);
        assertThat(stats.hitCount()).isEqualTo(2);
        assertThat(stats.judgedCount()).isEqualTo(3);
        assertThat(stats.hitRate()).isEqualTo(67); // 2 ÷ 3 = 66.7% → 67%
    }

    @Test
    void SOSO는_적중률에서_빼고_전체_기록_수에는_센다() {
        List<Observation> records = List.of(
                record(80, Rating.GOOD, false, false),  // 적중
                record(90, Rating.SOSO, false, false),
                record(20, Rating.SOSO, false, false));

        ObservationStatsResponse stats = ObservationStatsResponse.from(records);

        assertThat(stats.totalCount()).isEqualTo(3);
        assertThat(stats.judgedCount()).isEqualTo(1);
        assertThat(stats.hitRate()).isEqualTo(100);
    }

    @Test
    void 추천_장소_만족도는_추천_장소에서_본_기록_중_GOOD_비율() {
        List<Observation> records = List.of(
                record(90, Rating.GOOD, true, false),
                record(85, Rating.SOSO, true, true),
                record(70, Rating.GOOD, true, false),
                record(88, Rating.BAD, true, true),
                record(40, Rating.GOOD, false, true));  // 추천 장소가 아니라 만족도에서 뺌

        ObservationStatsResponse stats = ObservationStatsResponse.from(records);

        assertThat(stats.recommendationCount()).isEqualTo(4);
        assertThat(stats.recommendationSatisfaction()).isEqualTo(50); // 4곳 중 2곳 GOOD
        assertThat(stats.notificationCount()).isEqualTo(3);
    }

    @Test
    void 기록이_없으면_비율은_0이_아니라_null() {
        ObservationStatsResponse stats = ObservationStatsResponse.from(List.of());

        assertThat(stats.totalCount()).isZero();
        assertThat(stats.hitRate()).isNull();
        assertThat(stats.recommendationSatisfaction()).isNull();
    }

    @Test
    void SOSO만_있으면_적중률은_null() {
        ObservationStatsResponse stats = ObservationStatsResponse.from(List.of(record(80, Rating.SOSO, false, false)));

        assertThat(stats.totalCount()).isEqualTo(1);
        assertThat(stats.hitRate()).isNull();
    }
}
