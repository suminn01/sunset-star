package com.sunsetstar.location;

/**
 * GET /api/locations/region 응답. 좌표의 지역 이름이다 (기능명세서 F1).
 *
 * 예: {"latitude": 37.5552, "longitude": 126.9195, "name": "서울 마포구 서교동", "shortName": "마포구",
 *      "province": "서울특별시", "city": "마포구", "dong": "서교동"}
 *
 * @param latitude  요청한 위도
 * @param longitude 요청한 경도
 * @param name      화면에 보여 줄 이름. 시·도는 줄여 쓴다 (서울특별시 → 서울, 충청북도 → 충북)
 * @param shortName 상단 한 줄에 쓸 짧은 이름 (시·군·구, 예: "마포구", "춘천시"). 화면에서 "마포구 · 교외"처럼 쓴다
 * @param province  시·도 원래 이름 (예: "서울특별시")
 * @param city      시·군·구 (예: "마포구")
 * @param dong      행정동 (예: "서교동"). 바다 위처럼 없으면 빈 글자
 */
public record RegionResponse(
        double latitude,
        double longitude,
        String name,
        String shortName,
        String province,
        String city,
        String dong
) {
}
