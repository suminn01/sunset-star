package com.sunsetstar.location;

import com.sunsetstar.global.ApiException;
import com.sunsetstar.global.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 위치 검색과 좌표 → 지역명 (기능명세서 F1). LocationController가 부르는 곳이다.
 *
 * - search: 카카오 주소 검색 + 장소 검색을 합쳐서 결과 목록을 만든다 ("합정역", "망원동", 도로명 주소 모두 되도록)
 * - region: 좌표를 "서울 마포구 서교동" 같은 이름으로 바꾼다
 *
 * 카카오가 실패했을 때 (기능명세서 F8)
 * - search: 두 검색 중 하나만 실패하면 성공한 쪽 결과만 보여 준다. 둘 다 실패하면 503 LOCATION_UNAVAILABLE
 * - region: 503 LOCATION_UNAVAILABLE
 */
@Service
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    /** 검색 결과 최대 개수 */
    static final int MAX_RESULTS = 15;

    /** 같은 결과인지 볼 때 좌표를 소수점 넷째 자리(약 10m)까지 비교한다 */
    private static final double SAME_PLACE_SCALE = 10_000.0;

    /** 지역명 캐시 키를 맞추려고 좌표를 소수점 셋째 자리(약 100m)로 반올림한다. 동 경계가 바뀌지 않을 만큼 촘촘하게 */
    private static final double REGION_SCALE = 1_000.0;

    private final KakaoLocalClient client;

    public LocationService(KakaoLocalClient client) {
        this.client = client;
    }

    // ===== 위치 검색 =====

    /**
     * 주소 결과를 먼저, 장소 결과를 그다음에 놓고, 같은 이름·같은 좌표는 하나로 합친다.
     * 예: "망원동" → [서울 마포구 망원동(주소), 망원동 티라미수(장소), …]
     */
    public LocationSearchResponse search(String query) {
        String trimmed = query.trim();
        KakaoAddressResponse addresses = null;
        KakaoKeywordResponse places = null;
        try {
            addresses = client.searchAddress(trimmed);
        } catch (RestClientException e) {
            log.warn("카카오 주소 검색 실패: {}", e.getMessage());
        }
        try {
            places = client.searchKeyword(trimmed);
        } catch (RestClientException e) {
            log.warn("카카오 장소 검색 실패: {}", e.getMessage());
        }
        if (addresses == null && places == null) {
            throw new ApiException(ErrorCode.LOCATION_UNAVAILABLE);
        }

        // 순서를 지키면서 중복을 없애려고 LinkedHashMap을 쓴다 (넣은 순서대로 꺼내진다)
        Map<String, LocationSearchResponse.Result> results = new LinkedHashMap<>();
        if (addresses != null && addresses.documents() != null) {
            for (KakaoAddressResponse.Address address : addresses.documents()) {
                addResult(results, fromAddress(address));
            }
        }
        if (places != null && places.documents() != null) {
            for (KakaoKeywordResponse.Place place : places.documents()) {
                addResult(results, fromPlace(place));
            }
        }
        List<LocationSearchResponse.Result> list = results.values().stream()
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());
        return new LocationSearchResponse(trimmed, List.copyOf(list));
    }

    private void addResult(Map<String, LocationSearchResponse.Result> results, LocationSearchResponse.Result result) {
        if (result == null) {
            return;
        }
        String key = result.name() + "|" + roundTo(result.latitude(), SAME_PLACE_SCALE)
                + "|" + roundTo(result.longitude(), SAME_PLACE_SCALE);
        results.putIfAbsent(key, result);
    }

    private LocationSearchResponse.Result fromAddress(KakaoAddressResponse.Address address) {
        Double latitude = parse(address.y());
        Double longitude = parse(address.x());
        if (latitude == null || longitude == null) {
            return null;
        }
        String road = address.roadAddress() == null ? null : address.roadAddress().addressName();
        String detail = isBlank(road) ? address.addressName() : road;
        return new LocationSearchResponse.Result(
                address.addressName(), detail, latitude, longitude, LocationSearchResponse.Type.ADDRESS);
    }

    private LocationSearchResponse.Result fromPlace(KakaoKeywordResponse.Place place) {
        Double latitude = parse(place.y());
        Double longitude = parse(place.x());
        if (latitude == null || longitude == null) {
            return null;
        }
        String detail = isBlank(place.roadAddressName()) ? place.addressName() : place.roadAddressName();
        return new LocationSearchResponse.Result(
                place.placeName(), detail, latitude, longitude, LocationSearchResponse.Type.PLACE);
    }

    // ===== 좌표 → 지역명 =====

    /**
     * 좌표를 지역 이름으로 바꾼다. 행정동(H) 결과를 쓴다.
     *
     * @throws ApiException 404 LOCATION_NOT_FOUND: 카카오가 지역을 찾지 못했을 때 (예: 먼 바다, 한국 밖)
     * @throws ApiException 503 LOCATION_UNAVAILABLE: 카카오 요청이 실패했을 때
     */
    public RegionResponse region(double latitude, double longitude) {
        KakaoRegionResponse response;
        try {
            response = client.coordToRegion(roundTo(latitude, REGION_SCALE), roundTo(longitude, REGION_SCALE));
        } catch (RestClientException e) {
            log.warn("카카오 좌표 → 지역명 실패: {}", e.getMessage());
            throw new ApiException(ErrorCode.LOCATION_UNAVAILABLE);
        }
        if (response == null || response.documents() == null || response.documents().isEmpty()) {
            throw new ApiException(ErrorCode.LOCATION_NOT_FOUND);
        }
        KakaoRegionResponse.Region region = response.documents().stream()
                .filter(r -> KakaoRegionResponse.ADMINISTRATIVE.equals(r.regionType()))
                .findFirst()
                .orElse(response.documents().getFirst());

        String province = nullToEmpty(region.region1());
        String city = nullToEmpty(region.region2());
        String dong = nullToEmpty(region.region3());

        List<String> parts = new ArrayList<>();
        for (String part : List.of(shortProvince(province), city, dong)) {
            if (!part.isBlank()) {
                parts.add(part);
            }
        }
        if (parts.isEmpty()) {
            throw new ApiException(ErrorCode.LOCATION_NOT_FOUND);
        }
        String name = String.join(" ", parts);
        String shortName = city.isBlank() ? shortProvince(province) : city;
        return new RegionResponse(latitude, longitude, name, shortName, province, city, dong);
    }

    /**
     * 시·도 이름을 줄인다. 화면에서 "서울특별시 마포구 서교동" 대신 "서울 마포구 서교동"으로 보여 주려고.
     * 서울특별시 → 서울, 부산광역시 → 부산, 세종특별자치시 → 세종, 제주특별자치도 → 제주,
     * 충청북도 → 충북, 경상남도 → 경남, 경기도 → 경기
     */
    static String shortProvince(String province) {
        if (province == null || province.isBlank()) {
            return "";
        }
        for (String suffix : List.of("특별자치시", "특별자치도", "특별시", "광역시")) {
            if (province.endsWith(suffix)) {
                return province.substring(0, province.length() - suffix.length());
            }
        }
        if (province.length() == 4 && province.endsWith("도")) {
            // 충청북도 → 충 + 북
            return "" + province.charAt(0) + province.charAt(2);
        }
        if (province.endsWith("도")) {
            return province.substring(0, province.length() - 1); // 경기도 → 경기
        }
        return province;
    }

    // ===== 도우미 =====

    private Double parse(String number) {
        if (isBlank(number)) {
            return null;
        }
        try {
            return Double.parseDouble(number);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double roundTo(double value, double scale) {
        return Math.round(value * scale) / scale;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
