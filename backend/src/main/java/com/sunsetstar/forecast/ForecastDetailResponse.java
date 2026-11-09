package com.sunsetstar.forecast;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/forecast/detail 응답. 7일 카드를 눌렀을 때 나오는 상세 화면(기능명세서 F6, F4)용이다.
 *
 * - 노을: 0이 아닌 요소를 모두 "문장 + 점수"로 보여 주고, 마지막 줄에 기본 점수(50)를 둔다
 * - 별: 시간대별 점수 막대그래프, 추천 구간 강조, 달 모양(밝기)과 달 지는 시각
 *
 * @param latitude   요청한 위도
 * @param longitude  요청한 경도
 * @param light      빛 공해 단계
 * @param lightLabel 빛 공해 이름
 * @param basedAt    예보 기준 시각
 * @param date       날짜
 * @param reference  4일째부터 true ("참고용")
 * @param sunset     노을 상세. 없으면 null
 * @param star       별 상세. 없으면 null
 */
public record ForecastDetailResponse(
        double latitude,
        double longitude,
        LightPollution light,
        String lightLabel,
        LocalDateTime basedAt,
        LocalDate date,
        boolean reference,
        SunsetDetail sunset,
        StarDetail star
) {

    /**
     * "점수가 이렇게 나왔어요" 목록의 한 줄.
     * 예: {"name": "위쪽 구름", "points": 25, "reason": "높은 구름이 적당해서 분홍빛이 예쁘게 번질 것 같아요"}
     */
    public record FactorLine(String name, int points, String reason) {
    }

    /**
     * 노을 상세.
     *
     * @param baseScore 기본 점수 (50). 목록 마지막 줄에 "기본 50점"으로 보여 준다
     * @param factors   0이 아닌 요소들 (①→⑥ 순서)
     * @param missing   값이 없어서 빼고 계산한 요소 이름
     */
    public record SunsetDetail(
            int score,
            String grade,
            String gradeLine,
            LocalDateTime sunsetAt,
            LocalDateTime recommendFrom,
            LocalDateTime recommendTo,
            String note,
            int baseScore,
            List<FactorLine> factors,
            List<String> missing
    ) {
    }

    /**
     * 별 시간대별 막대그래프의 한 칸.
     *
     * @param time        시각 (정각, 그때부터 1시간)
     * @param score       그 시간의 별 점수
     * @param recommended 추천 구간에 들어가면 true (막대를 강조한다)
     */
    public record HourBar(LocalDateTime time, int score, boolean recommended) {
    }

    /**
     * 별 상세.
     *
     * @param moonIllumination 천문박명 끝 시각의 달 밝기 (0.0 그믐 ~ 1.0 보름달). 달 모양을 그릴 때 쓴다
     * @param moonSetAt        밤 동안 달이 지는 시각. 밤 시작에 달이 떠 있고 04시 전에 질 때만 있고, 아니면 null
     * @param hours            시간대별 점수
     * @param missing          값이 없어서 빼고 계산한 요소 이름 (어느 한 시간이라도 빠졌으면 포함)
     */
    public record StarDetail(
            int score,
            String grade,
            String gradeLine,
            String reason,
            LocalDateTime nightStart,
            LocalDateTime recommendFrom,
            LocalDateTime recommendTo,
            String note,
            String moonNote,
            String lightNote,
            double moonIllumination,
            LocalDateTime moonSetAt,
            List<HourBar> hours,
            List<String> missing
    ) {
    }
}
