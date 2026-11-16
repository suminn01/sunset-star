package com.sunsetstar.location;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 카카오 로컬 API에 요청을 보내고, 받은 JSON을 DTO에 담아 돌려주는 클래스.
 *
 * - searchKeyword: 장소 이름으로 검색 (예: "합정역")
 * - searchAddress: 주소로 검색 (예: "망원동", "월드컵로 212")
 * - coordToRegion: 좌표 → 행정구역 이름 (예: "서울 마포구 서교동")
 *
 * 모든 요청에 "Authorization: KakaoAK {REST API 키}" 헤더를 붙인다.
 *
 * 주의: 카카오는 x = 경도, y = 위도다. 이 클래스의 메서드는 다른 코드와 똑같이 (위도, 경도) 순서로 받고,
 * 카카오에 보낼 때만 x, y로 바꿔 끼운다.
 */
@Component
public class KakaoLocalClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoLocalClient.class);

    /** 장소 검색 한 번에 받을 개수 (카카오 최대 15) */
    private static final int KEYWORD_SIZE = 15;
    /** 주소 검색 한 번에 받을 개수 (카카오 최대 30, 기본 10) */
    private static final int ADDRESS_SIZE = 10;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;

    public KakaoLocalClient(KakaoProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                // 모든 요청에 키를 붙인다
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
                .build();
    }

    /**
     * 장소 이름으로 검색한다. 정확도순으로 최대 15개.
     * 예: searchKeyword("합정역")
     */
    public KakaoKeywordResponse searchKeyword(String query) {
        log.info("카카오 장소 검색: {}", query);
        return restClient.get()
                .uri(uri -> uri.path("/v2/local/search/keyword.json")
                        .queryParam("query", query)
                        .queryParam("size", KEYWORD_SIZE)
                        .build())
                .retrieve()
                .body(KakaoKeywordResponse.class);
    }

    /**
     * 주소로 검색한다. 최대 10개.
     * 예: searchAddress("망원동"), searchAddress("월드컵로 212")
     */
    public KakaoAddressResponse searchAddress(String query) {
        log.info("카카오 주소 검색: {}", query);
        return restClient.get()
                .uri(uri -> uri.path("/v2/local/search/address.json")
                        .queryParam("query", query)
                        .queryParam("size", ADDRESS_SIZE)
                        .build())
                .retrieve()
                .body(KakaoAddressResponse.class);
    }

    /**
     * 좌표를 행정구역 이름으로 바꾼다.
     * 예: coordToRegion(37.5665, 126.978) → "서울특별시 중구 …"
     *
     * 같은 좌표는 1시간 캐시한다. 부르는 쪽(LocationService)이 좌표를 반올림해서 넘긴다.
     */
    @Cacheable("kakao-region")
    public KakaoRegionResponse coordToRegion(double latitude, double longitude) {
        log.info("카카오 좌표 → 지역명: {}, {}", latitude, longitude);
        return restClient.get()
                .uri(uri -> uri.path("/v2/local/geo/coord2regioncode.json")
                        .queryParam("x", longitude) // x = 경도
                        .queryParam("y", latitude)  // y = 위도
                        .build())
                .retrieve()
                .body(KakaoRegionResponse.class);
    }
}
