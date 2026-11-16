package com.sunsetstar.forecast;

import java.util.List;

/**
 * 근처 장소 추천에서 점수를 매긴 곳 하나 (현재 위치 또는 후보 장소).
 *
 * 점수 = 날씨 점수 + 장소 보정, 0~100으로 자른다.
 *
 * @param candidate    후보 장소. 현재 위치면 null
 * @param weatherScore 날씨 점수 (노을 F2 또는 별 F4와 같은 계산. 별 후보는 짐작한 빛 공해로 계산한 값)
 * @param correction   장소 보정 합 (노을 높은 곳·트인 곳, 별 높은 곳, 최대 +15. 현재 위치 도심이면 −5)
 * @param score        최종 점수 (0~100)
 * @param reasons      장소 보정 이유 (예: "주변보다 높은 곳이에요", "서쪽이 트여 있을 가능성이 커요")
 * @param estimated    서쪽 시야나 빛 공해를 장소 종류로 짐작했으면 true. 화면에 "추정"을 붙인다
 */
public record ScoredPlace(
        NearbyCandidate candidate,
        int weatherScore,
        int correction,
        int score,
        List<String> reasons,
        boolean estimated
) {

    /** 현재 위치인지 */
    public boolean isCurrentLocation() {
        return candidate == null;
    }
}
