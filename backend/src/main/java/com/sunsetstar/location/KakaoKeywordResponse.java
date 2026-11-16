package com.sunsetstar.location;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 카카오 "키워드로 장소 검색"(/v2/local/search/keyword) 응답.
 * 예: "합정역" → 합정역 2호선, 합정역 6호선, …
 *
 * 카카오는 좌표를 x = 경도, y = 위도로 주고, 글자(String)로 준다. 숫자로 바꾸는 일은 LocationService가 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoKeywordResponse(Meta meta, List<Place> documents) {

    /**
     * @param totalCount 검색된 전체 개수
     * @param isEnd      마지막 페이지인지
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            @JsonProperty("total_count") Integer totalCount,
            @JsonProperty("is_end") Boolean isEnd
    ) {
    }

    /**
     * 장소 하나.
     *
     * @param id                카카오 장소 ID (같은 장소를 하나로 합칠 때 쓴다, F5)
     * @param placeName         장소 이름 (예: "합정역 2호선")
     * @param categoryName      분류 (예: "교통,수송 > 지하철,전철 > 수도권2호선")
     * @param categoryGroupCode 분류 코드 (예: "SW8" 지하철역)
     * @param addressName       지번 주소
     * @param roadAddressName   도로명 주소 (없으면 빈 글자)
     * @param x                 경도
     * @param y                 위도
     * @param placeUrl          카카오맵 장소 페이지 (길 찾기는 여기로 넘긴다, F5)
     * @param distance          중심 좌표에서의 거리(m). 중심 좌표를 보냈을 때만 온다
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Place(
            String id,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("category_group_code") String categoryGroupCode,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("road_address_name") String roadAddressName,
            String x,
            String y,
            @JsonProperty("place_url") String placeUrl,
            String distance
    ) {
    }
}
