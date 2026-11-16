package com.sunsetstar.forecast;

import com.sunsetstar.location.KakaoKeywordResponse;
import com.sunsetstar.location.KakaoLocalClient;
import com.sunsetstar.location.KakaoProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 근처 장소 후보 찾기(F5) 테스트. 카카오 대신 FakeKakao를 끼워서 인터넷 없이 확인한다.
 */
class NearbyCandidateServiceTest {

    private static final double LAT = 37.57;
    private static final double LON = 126.98;

    /** 키워드마다 돌려줄 장소를 정해 두는 가짜 카카오 */
    private static class FakeKakao extends KakaoLocalClient {
        final Map<String, List<KakaoKeywordResponse.Place>> byKeyword = new HashMap<>();
        final List<String> failingKeywords = new ArrayList<>();
        final List<Integer> radiusSeen = new ArrayList<>();

        FakeKakao() {
            super(new KakaoProperties("http://localhost", "test-key"));
        }

        @Override
        public KakaoKeywordResponse searchKeywordNear(String query, double latitude, double longitude,
                                                      Integer radiusMeters, int size) {
            radiusSeen.add(radiusMeters);
            if (failingKeywords.contains(query)) {
                throw new RestClientException("카카오 실패 (테스트)");
            }
            return new KakaoKeywordResponse(null, byKeyword.getOrDefault(query, List.of()));
        }
    }

    /** 장소 하나. distanceM은 카카오가 주는 거리(m) */
    private static KakaoKeywordResponse.Place place(String id, String name, int distanceM) {
        return new KakaoKeywordResponse.Place(id, name, "", "", "지번", "도로명 " + name,
                "126.9", "37.5", "https://place.map.kakao.com/" + id, String.valueOf(distanceM));
    }

    @Test
    @DisplayName("노을: 키워드 4개로 반경 10km 검색하고, 가까운 순으로 정렬한다")
    void sunsetKeywords() {
        FakeKakao kakao = new FakeKakao();
        kakao.byKeyword.put("전망대", List.of(place("1", "하늘 전망대", 5000)));
        kakao.byKeyword.put("공원", List.of(place("2", "동네 공원", 800)));

        List<NearbyCandidate> result = new NearbyCandidateService(kakao).findCandidates(SkyType.SUNSET, LAT, LON);

        assertThat(kakao.radiusSeen).hasSize(4).containsOnly(10_000);
        assertThat(result).extracting(NearbyCandidate::name).containsExactly("동네 공원", "하늘 전망대");
        assertThat(result.getFirst().distanceKm()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("같은 장소가 \"공원\"과 \"한강공원\"에 모두 걸리면 하나만 남기고, 트인 곳 키워드(한강공원)로 기억한다")
    void removeDuplicates() {
        FakeKakao kakao = new FakeKakao();
        kakao.byKeyword.put("공원", List.of(place("7", "망원한강공원", 2000)));
        kakao.byKeyword.put("한강공원", List.of(place("7", "망원한강공원", 2000)));

        List<NearbyCandidate> result = new NearbyCandidateService(kakao).findCandidates(SkyType.SUNSET, LAT, LON);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().keyword()).isEqualTo("한강공원");
    }

    @Test
    @DisplayName("별: 반경 없이 찾고 60km 밖은 뺀다")
    void starWithin60km() {
        FakeKakao kakao = new FakeKakao();
        kakao.byKeyword.put("천문대", List.of(place("1", "가까운 천문대", 45_000), place("2", "먼 천문대", 75_000)));

        List<NearbyCandidate> result = new NearbyCandidateService(kakao).findCandidates(SkyType.STAR, LAT, LON);

        assertThat(kakao.radiusSeen).hasSize(3).containsOnlyNulls();
        assertThat(result).extracting(NearbyCandidate::name).containsExactly("가까운 천문대");
    }

    @Test
    @DisplayName("후보는 가까운 순으로 최대 20곳")
    void max20() {
        FakeKakao kakao = new FakeKakao();
        List<KakaoKeywordResponse.Place> many = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            many.add(place("p" + i, "공원" + i, 100 * (30 - i)));
        }
        kakao.byKeyword.put("공원", many);

        List<NearbyCandidate> result = new NearbyCandidateService(kakao).findCandidates(SkyType.SUNSET, LAT, LON);

        assertThat(result).hasSize(NearbyRules.MAX_CANDIDATES);
        assertThat(result.getFirst().name()).isEqualTo("공원29"); // 가장 가까운 곳 (100m)
    }

    @Test
    @DisplayName("키워드 하나가 실패해도 나머지로 계속 찾는다")
    void oneKeywordFails() {
        FakeKakao kakao = new FakeKakao();
        kakao.failingKeywords.add("전망대");
        kakao.byKeyword.put("해수욕장", List.of(place("9", "을왕리해수욕장", 9000)));

        List<NearbyCandidate> result = new NearbyCandidateService(kakao).findCandidates(SkyType.SUNSET, LAT, LON);

        assertThat(result).extracting(NearbyCandidate::name).containsExactly("을왕리해수욕장");
    }

    @Test
    @DisplayName("모두 실패하면 빈 목록 (현재 위치 점수는 그대로 보여 줄 수 있게)")
    void allFail() {
        FakeKakao kakao = new FakeKakao();
        kakao.failingKeywords.addAll(NearbyRules.SUNSET_KEYWORDS);

        assertThat(new NearbyCandidateService(kakao).findCandidates(SkyType.SUNSET, LAT, LON)).isEmpty();
    }
}
