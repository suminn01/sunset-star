package com.sunsetstar.location;

import com.sunsetstar.global.ApiException;
import com.sunsetstar.global.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LocationService 테스트. 카카오 대신 FakeKakao를 끼워서 인터넷 없이 확인한다.
 */
class LocationServiceTest {

    /** 가짜 카카오. 돌려줄 응답과 실패 여부를 테스트마다 정한다. */
    private static class FakeKakao extends KakaoLocalClient {
        KakaoAddressResponse addresses = new KakaoAddressResponse(null, List.of());
        KakaoKeywordResponse places = new KakaoKeywordResponse(null, List.of());
        KakaoRegionResponse region = new KakaoRegionResponse(List.of());
        boolean addressFails;
        boolean keywordFails;
        boolean regionFails;
        double lastLatitude;
        double lastLongitude;

        FakeKakao() {
            super(new KakaoProperties("http://localhost", "test-key"));
        }

        @Override
        public KakaoAddressResponse searchAddress(String query) {
            if (addressFails) {
                throw new RestClientException("주소 검색 실패 (테스트)");
            }
            return addresses;
        }

        @Override
        public KakaoKeywordResponse searchKeyword(String query) {
            if (keywordFails) {
                throw new RestClientException("장소 검색 실패 (테스트)");
            }
            return places;
        }

        @Override
        public KakaoRegionResponse coordToRegion(double latitude, double longitude) {
            lastLatitude = latitude;
            lastLongitude = longitude;
            if (regionFails) {
                throw new RestClientException("지역명 실패 (테스트)");
            }
            return region;
        }
    }

    private static KakaoKeywordResponse.Place place(String name, String lat, String lon) {
        return new KakaoKeywordResponse.Place("id-" + name, name, "", "", "지번 " + name, "도로명 " + name,
                lon, lat, "https://place.map.kakao.com/1", null);
    }

    private static KakaoAddressResponse.Address address(String name, String lat, String lon) {
        return new KakaoAddressResponse.Address(name, "REGION", lon, lat, null);
    }

    @Nested
    @DisplayName("위치 검색")
    class Search {

        @Test
        @DisplayName("주소 결과가 먼저, 장소 결과가 그다음에 온다 (카카오는 x=경도, y=위도)")
        void addressFirst() {
            FakeKakao kakao = new FakeKakao();
            kakao.addresses = new KakaoAddressResponse(null, List.of(address("서울 마포구 망원동", "37.556", "126.904")));
            kakao.places = new KakaoKeywordResponse(null, List.of(place("망원한강공원", "37.555", "126.895")));

            LocationSearchResponse response = new LocationService(kakao).search("  망원동 ");

            assertThat(response.query()).isEqualTo("망원동"); // 앞뒤 공백 제거
            assertThat(response.results()).extracting(LocationSearchResponse.Result::name)
                    .containsExactly("서울 마포구 망원동", "망원한강공원");
            LocationSearchResponse.Result first = response.results().getFirst();
            assertThat(first.type()).isEqualTo(LocationSearchResponse.Type.ADDRESS);
            assertThat(first.latitude()).isEqualTo(37.556);
            assertThat(first.longitude()).isEqualTo(126.904);
            assertThat(response.results().get(1).address()).isEqualTo("도로명 망원한강공원");
        }

        @Test
        @DisplayName("같은 이름·같은 좌표는 한 번만 보여 준다")
        void removeDuplicates() {
            FakeKakao kakao = new FakeKakao();
            kakao.places = new KakaoKeywordResponse(null, List.of(
                    place("합정역", "37.54950", "126.91390"),
                    place("합정역", "37.54951", "126.91391")));

            LocationSearchResponse response = new LocationService(kakao).search("합정역");

            assertThat(response.results()).hasSize(1);
        }

        @Test
        @DisplayName("결과는 최대 15개")
        void maxResults() {
            FakeKakao kakao = new FakeKakao();
            List<KakaoKeywordResponse.Place> many = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                many.add(place("장소" + i, "37.5", String.valueOf(126.9 + i * 0.01)));
            }
            kakao.places = new KakaoKeywordResponse(null, many);

            assertThat(new LocationService(kakao).search("장소").results()).hasSize(LocationService.MAX_RESULTS);
        }

        @Test
        @DisplayName("주소 검색만 실패하면 장소 결과만 보여 준다")
        void oneFails() {
            FakeKakao kakao = new FakeKakao();
            kakao.addressFails = true;
            kakao.places = new KakaoKeywordResponse(null, List.of(place("합정역", "37.5495", "126.9139")));

            assertThat(new LocationService(kakao).search("합정역").results()).hasSize(1);
        }

        @Test
        @DisplayName("둘 다 실패하면 503 LOCATION_UNAVAILABLE")
        void bothFail() {
            FakeKakao kakao = new FakeKakao();
            kakao.addressFails = true;
            kakao.keywordFails = true;

            assertThatThrownBy(() -> new LocationService(kakao).search("합정역"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).errorCode()).isEqualTo(ErrorCode.LOCATION_UNAVAILABLE));
        }
    }

    @Nested
    @DisplayName("좌표 → 지역명")
    class Region {

        private final KakaoRegionResponse.Region legal =
                new KakaoRegionResponse.Region("B", "서울특별시 마포구 서교동", "서울특별시", "마포구", "서교동", "1144012000");
        private final KakaoRegionResponse.Region administrative =
                new KakaoRegionResponse.Region("H", "서울특별시 마포구 서교동", "서울특별시", "마포구", "서교동", "1144066000");

        @Test
        @DisplayName("행정동(H)을 골라 \"서울 마포구 서교동\", 짧은 이름 \"마포구\"")
        void name() {
            FakeKakao kakao = new FakeKakao();
            kakao.region = new KakaoRegionResponse(List.of(legal, administrative));

            RegionResponse response = new LocationService(kakao).region(37.55521, 126.91955);

            assertThat(response.name()).isEqualTo("서울 마포구 서교동");
            assertThat(response.shortName()).isEqualTo("마포구");
            assertThat(response.province()).isEqualTo("서울특별시");
            // 캐시 키를 맞추려고 소수점 셋째 자리로 반올림해서 카카오에 묻는다
            assertThat(kakao.lastLatitude).isEqualTo(37.555);
            assertThat(kakao.lastLongitude).isEqualTo(126.92);
        }

        @Test
        @DisplayName("카카오가 지역을 못 찾으면 404 LOCATION_NOT_FOUND")
        void notFound() {
            FakeKakao kakao = new FakeKakao();

            assertThatThrownBy(() -> new LocationService(kakao).region(33.0, 125.0))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).errorCode()).isEqualTo(ErrorCode.LOCATION_NOT_FOUND));
        }

        @Test
        @DisplayName("카카오가 실패하면 503 LOCATION_UNAVAILABLE")
        void failure() {
            FakeKakao kakao = new FakeKakao();
            kakao.regionFails = true;

            assertThatThrownBy(() -> new LocationService(kakao).region(37.5, 127.0))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).errorCode()).isEqualTo(ErrorCode.LOCATION_UNAVAILABLE));
        }

        @Test
        @DisplayName("시·도 이름 줄이기")
        void shortProvince() {
            assertThat(LocationService.shortProvince("서울특별시")).isEqualTo("서울");
            assertThat(LocationService.shortProvince("부산광역시")).isEqualTo("부산");
            assertThat(LocationService.shortProvince("세종특별자치시")).isEqualTo("세종");
            assertThat(LocationService.shortProvince("제주특별자치도")).isEqualTo("제주");
            assertThat(LocationService.shortProvince("강원특별자치도")).isEqualTo("강원");
            assertThat(LocationService.shortProvince("충청북도")).isEqualTo("충북");
            assertThat(LocationService.shortProvince("경상남도")).isEqualTo("경남");
            assertThat(LocationService.shortProvince("경기도")).isEqualTo("경기");
        }
    }
}
