package com.sunsetstar.location;

import java.util.List;

/**
 * GET /api/locations/search 응답. 위치 검색창의 결과 목록이다 (기능명세서 F1).
 *
 * 예: {"query": "합정역", "results": [{"name": "합정역 2호선", "address": "서울 마포구 양화로 지하 45",
 *       "latitude": 37.5495, "longitude": 126.9139, "type": "PLACE"}, ...]}
 *
 * 사용자가 하나를 고르면 프런트는 그 결과의 위도·경도를 저장해서 예보 API에 쓴다.
 *
 * @param query   검색어
 * @param results 검색 결과 (주소 결과가 먼저, 그다음 장소 결과, 최대 15개)
 */
public record LocationSearchResponse(String query, List<Result> results) {

    /** 결과 종류 */
    public enum Type {
        /** 주소로 찾은 결과 (예: "서울 마포구 망원동") */
        ADDRESS,
        /** 장소 이름으로 찾은 결과 (예: "합정역 2호선") */
        PLACE
    }

    /**
     * 검색 결과 하나.
     *
     * @param name      목록에 크게 보여 줄 이름 (장소 이름 또는 주소)
     * @param address   그 아래 작게 보여 줄 주소 (도로명 주소가 있으면 도로명)
     * @param latitude  위도
     * @param longitude 경도
     * @param type      ADDRESS 또는 PLACE
     */
    public record Result(String name, String address, double latitude, double longitude, Type type) {
    }
}
