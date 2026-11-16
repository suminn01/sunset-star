package com.sunsetstar.forecast;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/nearby 응답. 오늘 카드 아래의 "현재 위치 vs 추천 장소" 비교 카드다 (기능명세서 F5, F6).
 *
 * 화면 예:
 *   현재 위치 62점 · 그럭저럭
 *   ○○ 전망대 89점 · +27점 · 약 5km · 주변보다 높은 곳이에요 · 서쪽이 트여 있을 가능성이 커요(추정)
 *
 * @param type           SUNSET(노을) 또는 STAR(별)
 * @param typeLabel      "노을" 또는 "별"
 * @param date           날짜
 * @param current        현재 위치 점수
 * @param recommendations 추천 장소 (점수 차이가 큰 순, 최대 3곳). 없으면 빈 목록
 * @param message        추천이 없을 때 안내 문구. 추천이 있으면 null
 *                       ("지금 있는 곳도 충분히 좋아요", "오늘은 근처 어디도 쉬는 날이에요", "지금은 근처 장소를 찾지 못했어요")
 */
public record NearbyResponse(
        SkyType type,
        String typeLabel,
        LocalDate date,
        Current current,
        List<Recommendation> recommendations,
        String message
) {

    /**
     * 현재 위치.
     *
     * @param score 점수 (도심이면 노을 −5 보정이 들어간 값)
     * @param grade 등급 이름 (예: "그럭저럭")
     */
    public record Current(int score, String grade) {
    }

    /**
     * 추천 장소 하나.
     *
     * @param name       장소 이름
     * @param address    주소
     * @param latitude   위도
     * @param longitude  경도
     * @param distanceKm 현재 위치에서의 거리 (km, 소수점 첫째 자리). 화면에 "약 5km"로 보여 준다
     * @param score      점수
     * @param grade      등급 이름
     * @param gain       현재 위치보다 몇 점 높은지 (예: 27 → "+27점")
     * @param reasons    추천 이유 (예: ["주변보다 높은 곳이에요", "서쪽이 트여 있을 가능성이 커요"])
     * @param estimated  짐작한 값이 들어 있으면 true. 화면에 "(추정)"을 붙인다
     * @param placeUrl   카카오맵 장소 페이지. 길 찾기는 여기로 넘긴다
     */
    public record Recommendation(
            String name,
            String address,
            double latitude,
            double longitude,
            double distanceKm,
            int score,
            String grade,
            int gain,
            List<String> reasons,
            boolean estimated,
            String placeUrl
    ) {
    }
}
