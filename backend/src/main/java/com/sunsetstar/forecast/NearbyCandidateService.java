package com.sunsetstar.forecast;

import com.sunsetstar.location.KakaoKeywordResponse;
import com.sunsetstar.location.KakaoLocalClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sunsetstar.forecast.NearbyRules.*;

/**
 * 근처 관측 장소 후보를 찾는다 (기능명세서 F5 "후보 검색 규칙").
 *
 * 1. 종류(노을/별)에 맞는 키워드마다 카카오 근처 장소 검색 (가까운 순)
 * 2. 같은 장소가 여러 키워드에 걸리면 카카오 장소 ID로 하나만 남긴다
 * 3. 별은 60km 안의 곳만 남긴다 (노을은 검색 반경 10km로 이미 걸러진다)
 * 4. 가까운 순으로 최대 20곳
 *
 * 결과는 좌표·종류마다 하루 캐시한다 (장소는 자주 바뀌지 않으니 카카오 호출을 줄인다).
 * 키워드 하나가 실패하면 나머지 키워드로 계속 찾고, 모두 실패하면 빈 목록을 돌려준다.
 * 후보가 없어도 현재 위치 점수는 정상으로 보여 줘야 하기 때문이다 (F5 완료 기준).
 */
@Service
public class NearbyCandidateService {

    private static final Logger log = LoggerFactory.getLogger(NearbyCandidateService.class);

    private final KakaoLocalClient kakao;

    public NearbyCandidateService(KakaoLocalClient kakao) {
        this.kakao = kakao;
    }

    /**
     * 근처 후보 장소를 찾는다. 좌표는 부르는 쪽에서 소수점 둘째 자리로 반올림해서 넘긴다 (캐시 키 통일).
     */
    @Cacheable(cacheNames = "nearby-candidates", key = "#type.name() + ':' + #latitude + ':' + #longitude")
    public List<NearbyCandidate> findCandidates(SkyType type, double latitude, double longitude) {
        Coordinate center = new Coordinate(latitude, longitude);
        List<String> keywords = type == SkyType.SUNSET ? SUNSET_KEYWORDS : STAR_KEYWORDS;
        Integer radius = type == SkyType.SUNSET ? SUNSET_RADIUS_M : null; // 별은 반경 없이 가까운 순

        // 카카오 장소 ID → 후보. 넣은 순서를 지키려고 LinkedHashMap
        Map<String, NearbyCandidate> candidates = new LinkedHashMap<>();
        for (String keyword : keywords) {
            KakaoKeywordResponse response;
            try {
                response = kakao.searchKeywordNear(keyword, latitude, longitude, radius, PLACES_PER_KEYWORD);
            } catch (RestClientException e) {
                log.warn("근처 장소 검색 실패 ({}): {}", keyword, e.getMessage());
                continue;
            }
            if (response == null || response.documents() == null) {
                continue;
            }
            for (KakaoKeywordResponse.Place place : response.documents()) {
                NearbyCandidate candidate = toCandidate(place, keyword, center);
                if (candidate == null) {
                    continue;
                }
                NearbyCandidate existing = candidates.get(candidate.kakaoId());
                // 같은 장소면 보정을 더 잘 받는 키워드로 찾은 쪽을 남긴다 (예: "공원"보다 "한강공원")
                if (existing == null || bonusRank(type, keyword) > bonusRank(type, existing.keyword())) {
                    candidates.put(candidate.kakaoId(), candidate);
                }
            }
        }

        return candidates.values().stream()
                .filter(c -> type == SkyType.SUNSET || c.distanceKm() <= STAR_MAX_DISTANCE_KM)
                .sorted(Comparator.comparingDouble(NearbyCandidate::distanceKm))
                .limit(MAX_CANDIDATES)
                .toList();
    }

    private NearbyCandidate toCandidate(KakaoKeywordResponse.Place place, String keyword, Coordinate center) {
        if (place.id() == null) {
            return null;
        }
        Double latitude = parse(place.y());
        Double longitude = parse(place.x());
        if (latitude == null || longitude == null) {
            return null;
        }
        Coordinate coordinate = new Coordinate(latitude, longitude);
        // 카카오가 준 거리(m)가 있으면 그걸 쓰고, 없으면 직접 계산한다
        Double distanceM = parse(place.distance());
        double distanceKm = distanceM != null ? distanceM / 1000.0 : center.distanceKm(coordinate);
        String address = place.roadAddressName() == null || place.roadAddressName().isBlank()
                ? place.addressName() : place.roadAddressName();
        return new NearbyCandidate(place.id(), place.placeName(), keyword, coordinate, distanceKm,
                address, place.placeUrl());
    }

    /**
     * 키워드가 보정을 얼마나 잘 받는지 순위. 같은 장소가 여러 키워드에 걸렸을 때 고르는 기준.
     * - 노을: 트인 곳 키워드(전망대·해수욕장·한강공원)가 위
     * - 별: 시골로 짐작하는 키워드(천문대·휴양림)가 교외(캠핑장)보다 위
     */
    private int bonusRank(SkyType type, String keyword) {
        if (type == SkyType.SUNSET) {
            return OPEN_VIEW_KEYWORDS.contains(keyword) ? 1 : 0;
        }
        LightPollution light = ESTIMATED_LIGHT.get(keyword);
        return light == null ? 0 : (int) Math.round(light.factor() * 100);
    }

    private Double parse(String number) {
        if (number == null || number.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(number);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
