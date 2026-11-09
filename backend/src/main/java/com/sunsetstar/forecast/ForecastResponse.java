package com.sunsetstar.forecast;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/forecast 응답. 7일 예보 카드(기능명세서 F6) 화면이 그대로 쓰는 모양이다.
 *
 * 프런트에서 받는 JSON 예:
 * {
 *   "latitude": 37.57, "longitude": 126.98, "light": "SUBURB", "lightLabel": "교외",
 *   "basedAt": "2026-11-09T15:00",
 *   "days": [
 *     { "date": "2026-11-09", "reference": false,
 *       "sunset": { "score": 75, "grade": "볼 만해요", "gradeLine": "오늘 하늘 볼 만해요",
 *                   "reason": "높은 구름이 적당해서 분홍빛이 예쁘게 번질 것 같아요",
 *                   "sunsetAt": "2026-11-09T17:27", "recommendFrom": "...T17:12", "recommendTo": "...T17:47",
 *                   "note": "해가 진 뒤에도 바로 떠나지 마세요", "partial": false },
 *       "star": { "score": 86, "grade": "꼭 보세요", ... } },
 *     ...
 *   ]
 * }
 *
 * @param latitude   요청한 위도
 * @param longitude  요청한 경도
 * @param light      빛 공해 단계 (CITY / SUBURB / RURAL)
 * @param lightLabel 빛 공해 이름 (도심 / 교외 / 시골)
 * @param basedAt    예보 기준 시각. 화면에 "오후 3시 예보 기준"처럼 작게 보여 준다
 * @param days       날짜별 카드 (오늘부터 7일)
 */
public record ForecastResponse(
        double latitude,
        double longitude,
        LightPollution light,
        String lightLabel,
        LocalDateTime basedAt,
        List<DayCard> days
) {

    /**
     * 날짜 하나의 카드.
     *
     * @param date      날짜
     * @param reference 4일째부터 true. 카드에 "참고용"을 표시한다
     * @param sunset    노을 부분. 일몰 시각을 못 받은 날은 null
     * @param star      별 부분. 밤 시간 예보가 없는 날은 null
     */
    public record DayCard(
            LocalDate date,
            boolean reference,
            SunsetCard sunset,
            StarCard star
    ) {
    }

    /**
     * 카드의 노을 부분.
     *
     * @param score         노을 점수 (0~100)
     * @param grade         등급 이름 (예: "볼 만해요")
     * @param gradeLine     등급 한 줄 (예: "오늘 하늘 볼 만해요")
     * @param reason        이유 한 줄. 점수에 가장 크게 영향을 준 요소의 문장. 없으면 null
     * @param sunsetAt      일몰 시각
     * @param recommendFrom 추천 시작 (쉬는 날이면 null)
     * @param recommendTo   추천 끝 (쉬는 날이면 null)
     * @param note          "해가 진 뒤에도 바로 떠나지 마세요" 또는 "오늘은 쉬어요"
     * @param partial       일부 정보가 없어서 빼고 계산했으면 true. 카드에 "일부 정보 없음"을 표시한다
     */
    public record SunsetCard(
            int score,
            String grade,
            String gradeLine,
            String reason,
            LocalDateTime sunsetAt,
            LocalDateTime recommendFrom,
            LocalDateTime recommendTo,
            String note,
            boolean partial
    ) {
    }

    /**
     * 카드의 별 부분.
     *
     * @param score         하루 대표 점수 (시간별 최고점)
     * @param grade         등급 이름
     * @param gradeLine     등급 한 줄 (예: "오늘 밤 별 볼 만해요")
     * @param reason        이유 한 줄. 추천 구간 첫 시간에서 점수를 가장 많이 깎은 요소의 문장
     * @param nightStart    천문박명이 끝나는 시각
     * @param recommendFrom 추천 구간 시작 (쉬는 날이면 null)
     * @param recommendTo   추천 구간 끝 (쉬는 날이면 null)
     * @param note          쉬는 날이면 "오늘 밤은 쉬어 가는 날이에요"
     * @param moonNote      "달이 01:10에 지면 더 좋아요" (해당할 때만)
     * @param lightNote     빛 공해 문장 (해당할 때만)
     * @param partial       일부 정보가 없어서 빼고 계산한 시간이 있으면 true
     */
    public record StarCard(
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
            boolean partial
    ) {
    }
}
