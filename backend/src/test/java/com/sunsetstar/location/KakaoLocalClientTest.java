package com.sunsetstar.location;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * KakaoLocalClient가 실제 카카오 API에서 값을 받아오는지 확인하는 테스트.
 *
 * 진짜 요청을 보내므로 인터넷과 카카오 REST API 키가 필요하다.
 * 키는 application-secret.yml에서 읽는다. 키가 없으면(다른 컴퓨터, GitHub 등) 실패하지 않고 건너뛴다.
 */
class KakaoLocalClientTest {

    private KakaoLocalClient client;

    @BeforeEach
    void setUp() {
        String key = readKeyFromSecretFile();
        assumeThat(key).as("application-secret.yml에 kakao.rest-api-key가 없어서 건너뜁니다").isNotBlank();
        client = new KakaoLocalClient(new KakaoProperties("https://dapi.kakao.com", key));
    }

    /** application-secret.yml에서 kakao.rest-api-key를 읽는다. 파일이나 값이 없으면 null */
    private String readKeyFromSecretFile() {
        ClassPathResource secret = new ClassPathResource("application-secret.yml");
        if (!secret.exists()) {
            return null;
        }
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(secret);
        Properties properties = yaml.getObject();
        return properties == null ? null : properties.getProperty("kakao.rest-api-key");
    }

    @Test
    @DisplayName("장소 검색: \"합정역\"으로 찾으면 합정역이 나오고 좌표가 서울 서쪽이다")
    void searchKeyword() {
        KakaoKeywordResponse response = client.searchKeyword("합정역");

        assertThat(response.documents()).isNotEmpty();
        KakaoKeywordResponse.Place first = response.documents().getFirst();
        assertThat(first.placeName()).contains("합정역");
        // y = 위도, x = 경도. 합정역은 대략 북위 37.55, 동경 126.91
        assertThat(Double.parseDouble(first.y())).isBetween(37.5, 37.6);
        assertThat(Double.parseDouble(first.x())).isBetween(126.85, 126.95);
    }

    @Test
    @DisplayName("장소 검색: \"망원\"으로도 결과가 나온다 (6단계 완료 기준)")
    void searchMangwon() {
        KakaoKeywordResponse response = client.searchKeyword("망원");

        assertThat(response.documents()).isNotEmpty();
    }

    @Test
    @DisplayName("주소 검색: \"망원동\"은 서울 마포구 망원동")
    void searchAddress() {
        KakaoAddressResponse response = client.searchAddress("망원동");

        assertThat(response.documents()).isNotEmpty();
        assertThat(response.documents())
                .anySatisfy(address -> assertThat(address.addressName()).contains("마포구 망원동"));
    }

    @Test
    @DisplayName("좌표 → 지역명: 서울시청 좌표는 서울특별시 중구")
    void coordToRegion() {
        KakaoRegionResponse response = client.coordToRegion(37.5665, 126.978);

        assertThat(response.documents()).isNotEmpty();
        KakaoRegionResponse.Region administrative = response.documents().stream()
                .filter(region -> KakaoRegionResponse.ADMINISTRATIVE.equals(region.regionType()))
                .findFirst()
                .orElseThrow();
        assertThat(administrative.region1()).isEqualTo("서울특별시");
        assertThat(administrative.region2()).isEqualTo("중구");
    }
}
