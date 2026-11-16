package com.sunsetstar.forecast;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 근처 관측 장소 추천(기능명세서 F5)의 기준 숫자와 문장을 한곳에 모은 클래스.
 *
 * 후보 점수 = 날씨 점수(F2 노을 또는 F4 별과 같은 계산) + 장소 보정, 0~100으로 자른다.
 * 현재 위치보다 10점 이상 높은 후보를 점수 차이가 큰 순으로 최대 3곳 추천한다.
 *
 * 숫자는 모두 기능명세서 Ver.1.5의 초기값이다. 조정할 때는 이 파일만 고친다.
 */
public final class NearbyRules {

    private NearbyRules() {
    }

    // ===== 후보 검색 (카카오 키워드 검색) =====

    /** 노을: 퇴근길·하교길에 들를 수 있는 가까운 곳 */
    public static final List<String> SUNSET_KEYWORDS = List.of("전망대", "공원", "한강공원", "해수욕장");
    /** 노을 후보 검색 반경 (m) */
    public static final int SUNSET_RADIUS_M = 10_000;

    /** 별: 주말에 차로 다녀올 만한 어두운 곳 */
    public static final List<String> STAR_KEYWORDS = List.of("천문대", "휴양림", "캠핑장");
    /** 별 후보는 반경 없이 거리순으로 찾은 뒤 이 거리(km) 안의 곳만 쓴다 */
    public static final double STAR_MAX_DISTANCE_KM = 60.0;

    /** 키워드 하나에 받을 장소 수 (카카오 최대 15) */
    public static final int PLACES_PER_KEYWORD = 15;
    /** 중복을 없앤 뒤 가까운 순으로 쓸 후보 수 */
    public static final int MAX_CANDIDATES = 20;

    // ===== 장소 보정: 노을 =====

    /** 후보 고도가 현재 위치보다 50m 이상 높으면 +5, 150m 이상 높으면 +10 */
    public static final double SUNSET_HIGHER_M = 50.0;
    public static final int SUNSET_HIGHER_POINTS = 5;
    public static final double SUNSET_MUCH_HIGHER_M = 150.0;
    public static final int SUNSET_MUCH_HIGHER_POINTS = 10;
    public static final String HIGHER_REASON = "주변보다 높은 곳이에요";

    /** 전망대, 해수욕장, 강변(한강공원)은 서쪽이 트여 있을 가능성이 커서 +5 */
    public static final Set<String> OPEN_VIEW_KEYWORDS = Set.of("전망대", "해수욕장", "한강공원");
    public static final int OPEN_VIEW_POINTS = 5;
    public static final String OPEN_VIEW_REASON = "서쪽이 트여 있을 가능성이 커요";

    /** 현재 위치가 도심이면 건물에 서쪽이 가려질 수 있어서 현재 위치 노을 점수 −5 */
    public static final int CITY_CURRENT_LOCATION_POINTS = -5;

    // ===== 장소 보정: 별 =====

    /** 장소 종류로 빛 공해를 추정한다. 천문대·휴양림은 시골, 캠핑장은 교외로 보고 F4 계수를 다시 적용 */
    public static final Map<String, LightPollution> ESTIMATED_LIGHT = Map.of(
            "천문대", LightPollution.RURAL,
            "휴양림", LightPollution.RURAL,
            "캠핑장", LightPollution.SUBURB
    );
    public static final String DARK_REASON = "불빛이 적은 곳일 가능성이 커요";

    /** 후보 고도가 500m 이상이면 +5 */
    public static final double STAR_HIGH_ALTITUDE_M = 500.0;
    public static final int STAR_HIGH_ALTITUDE_POINTS = 5;
    public static final String HIGH_ALTITUDE_REASON = "해발 500m가 넘는 높은 곳이에요";

    /** 장소 보정은 합쳐서 최대 +15 */
    public static final int MAX_CORRECTION = 15;

    // ===== 추천 =====

    /** 현재 위치보다 이 점수 이상 높아야 추천한다 */
    public static final int MIN_GAIN = 10;
    /** 최대 추천 수 */
    public static final int MAX_RECOMMENDATIONS = 3;
    /** 추천할 곳이 없을 때 */
    public static final String NO_BETTER_MESSAGE = "지금 있는 곳도 충분히 좋아요";
    /** 현재 위치와 추천 장소가 모두 이 점수 미만이면 쉬는 날 안내 */
    public static final int ALL_POOR_BELOW = 40;
    public static final String ALL_POOR_MESSAGE = "오늘은 근처 어디도 쉬는 날이에요";
    /** 서쪽 시야와 빛 공해는 고도와 장소 종류로 추정한 값이라 화면에 붙이는 표시 */
    public static final String ESTIMATE_LABEL = "추정";
}
