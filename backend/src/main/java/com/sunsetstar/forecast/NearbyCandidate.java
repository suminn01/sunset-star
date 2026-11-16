package com.sunsetstar.forecast;

/**
 * 근처 관측 장소 후보 하나 (기능명세서 F5). 카카오 장소 검색으로 찾는다.
 *
 * @param kakaoId    카카오 장소 ID (같은 장소를 하나로 합칠 때 쓴다)
 * @param name       장소 이름 (예: "노을공원")
 * @param keyword    어떤 키워드로 찾았는지 (예: "전망대"). 트인 곳·빛 공해를 짐작할 때 쓴다
 * @param coordinate 좌표
 * @param distanceKm 현재 위치에서의 거리 (km)
 * @param address    주소 (도로명이 있으면 도로명)
 * @param placeUrl   카카오맵 장소 페이지. 길 찾기는 여기로 넘긴다
 */
public record NearbyCandidate(
        String kakaoId,
        String name,
        String keyword,
        Coordinate coordinate,
        double distanceKm,
        String address,
        String placeUrl
) {
}
