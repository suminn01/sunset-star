package com.sunsetstar.location;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 카카오 "주소 검색"(/v2/local/search/address) 응답.
 * 예: "망원동" → 서울 마포구 망원동, "월드컵로 212" → 그 도로명 주소
 *
 * 좌표는 x = 경도, y = 위도, 글자(String)로 온다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoAddressResponse(KakaoKeywordResponse.Meta meta, List<Address> documents) {

    /**
     * 주소 하나.
     *
     * @param addressName 전체 주소 (예: "서울 마포구 망원동")
     * @param addressType REGION(지명) / ROAD(도로명) / REGION_ADDR(지번 주소) / ROAD_ADDR(도로명 주소)
     * @param x           경도
     * @param y           위도
     * @param roadAddress 도로명 주소 정보. 지명만 검색하면 null
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Address(
            @JsonProperty("address_name") String addressName,
            @JsonProperty("address_type") String addressType,
            String x,
            String y,
            @JsonProperty("road_address") RoadAddress roadAddress
    ) {
    }

    /**
     * @param addressName  도로명 주소 (예: "서울 마포구 월드컵로 212")
     * @param buildingName 건물 이름 (없으면 빈 글자)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoadAddress(
            @JsonProperty("address_name") String addressName,
            @JsonProperty("building_name") String buildingName
    ) {
    }
}
