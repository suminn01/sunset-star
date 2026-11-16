package com.sunsetstar.location;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 카카오 "좌표로 행정구역정보 받기"(/v2/local/geo/coord2regioncode) 응답.
 * 예: 서울시청 좌표 → "서울특별시 중구 …"
 *
 * 보통 두 개가 온다.
 * - region_type "H": 행정동 (주민센터 기준, 화면에 보여 줄 이름은 이쪽)
 * - region_type "B": 법정동 (주소에 쓰는 이름)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoRegionResponse(List<Region> documents) {

    /** 행정동 */
    public static final String ADMINISTRATIVE = "H";

    /**
     * 행정구역 하나.
     *
     * @param regionType  H(행정동) 또는 B(법정동)
     * @param addressName 전체 이름 (예: "서울특별시 마포구 서교동")
     * @param region1     시·도 (예: "서울특별시")
     * @param region2     시·군·구 (예: "마포구")
     * @param region3     읍·면·동 (예: "서교동"). 바다 위면 빈 글자
     * @param code        행정구역 코드
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Region(
            @JsonProperty("region_type") String regionType,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("region_1depth_name") String region1,
            @JsonProperty("region_2depth_name") String region2,
            @JsonProperty("region_3depth_name") String region3,
            String code
    ) {
    }
}
