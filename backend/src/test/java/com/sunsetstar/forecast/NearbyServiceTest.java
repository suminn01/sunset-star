package com.sunsetstar.forecast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 근처 장소 추천 규칙(F5 "추천과 비교 규칙") 테스트.
 * 점수가 이미 매겨진 상태에서 무엇을 추천하는지만 본다 (외부 호출 없음).
 */
class NearbyServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 16);

    private static ScoredPlace current(int score) {
        return new ScoredPlace(null, score, 0, score, List.of(), false);
    }

    private static ScoredPlace place(String name, int score, double distanceKm) {
        NearbyCandidate candidate = new NearbyCandidate("id-" + name, name, "전망대",
                new Coordinate(37.5, 126.9), distanceKm, "주소 " + name, "https://place.map.kakao.com/1");
        return new ScoredPlace(candidate, score, 0, score, List.of(NearbyRules.OPEN_VIEW_REASON), true);
    }

    private static NearbyResponse recommend(ScoredPlace current, ScoredPlace... candidates) {
        return NearbyService.recommend(SkyType.SUNSET, DAY, new NearbyScorer.Scores(current, List.of(candidates)));
    }

    @Test
    @DisplayName("현재 위치보다 10점 이상 높아야 추천한다 (70점 기준: 79점 ✕, 80점 ○)")
    void minGain() {
        NearbyResponse response = recommend(current(70), place("A", 79, 1), place("B", 80, 2));

        assertThat(response.recommendations()).extracting(NearbyResponse.Recommendation::name).containsExactly("B");
        assertThat(response.recommendations().getFirst().gain()).isEqualTo(10);
        assertThat(response.message()).isNull();
    }

    @Test
    @DisplayName("점수 차이가 큰 순, 같으면 가까운 순으로 최대 3곳")
    void orderAndLimit() {
        NearbyResponse response = recommend(current(50),
                place("먼 80점", 80, 5.0),
                place("가까운 80점", 80, 2.0),
                place("90점", 90, 9.0),
                place("75점", 75, 1.0));

        assertThat(response.recommendations()).extracting(NearbyResponse.Recommendation::name)
                .containsExactly("90점", "가까운 80점", "먼 80점");
    }

    @Test
    @DisplayName("추천할 곳이 없으면 \"지금 있는 곳도 충분히 좋아요\"")
    void noBetter() {
        NearbyResponse response = recommend(current(85), place("A", 88, 1));

        assertThat(response.recommendations()).isEmpty();
        assertThat(response.message()).isEqualTo(NearbyRules.NO_BETTER_MESSAGE);
        assertThat(response.current().score()).isEqualTo(85);
        assertThat(response.current().grade()).isEqualTo("꼭 보세요");
    }

    @Test
    @DisplayName("현재 위치 20점, 제일 좋은 곳도 35점이면 추천 없이 \"오늘은 근처 어디도 쉬는 날이에요\"")
    void allPoor() {
        NearbyResponse response = recommend(current(20), place("A", 35, 1));

        assertThat(response.recommendations()).isEmpty();
        assertThat(response.message()).isEqualTo(NearbyRules.ALL_POOR_MESSAGE);
    }

    @Test
    @DisplayName("현재 위치는 20점이어도 45점인 곳이 있으면 추천한다")
    void poorButOneGood() {
        NearbyResponse response = recommend(current(20), place("A", 45, 1));

        assertThat(response.recommendations()).hasSize(1);
    }

    @Test
    @DisplayName("후보가 하나도 없으면(카카오 실패 등) \"지금은 근처 장소를 찾지 못했어요\", 현재 위치 점수는 그대로")
    void noCandidates() {
        NearbyResponse response = recommend(current(62));

        assertThat(response.recommendations()).isEmpty();
        assertThat(response.message()).isEqualTo(NearbyService.NO_PLACES_MESSAGE);
        assertThat(response.current().score()).isEqualTo(62);
    }

    @Test
    @DisplayName("추천 장소 정보: 거리는 소수점 첫째 자리, 이유와 추정 표시, 카카오맵 링크")
    void recommendationFields() {
        NearbyResponse response = recommend(current(62), place("하늘 전망대", 89, 4.96));

        NearbyResponse.Recommendation first = response.recommendations().getFirst();
        assertThat(first.score()).isEqualTo(89);
        assertThat(first.gain()).isEqualTo(27);
        assertThat(first.distanceKm()).isEqualTo(5.0);
        assertThat(first.reasons()).containsExactly(NearbyRules.OPEN_VIEW_REASON);
        assertThat(first.estimated()).isTrue();
        assertThat(first.placeUrl()).startsWith("https://place.map.kakao.com/");
    }
}
