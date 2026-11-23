package com.sunsetstar.observation;

import com.sunsetstar.forecast.Factor;
import com.sunsetstar.forecast.SkyType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관측 기록 한 건 응답. 등록 결과와 내 기록 목록에 같이 쓴다.
 *
 * @param hit      예측이 맞았는지. true 적중 / false 빗나감 / null 판정 안 함(SOSO)
 * @param photoUrl 사진 주소 (/api/observations/{id}/photo). 사진이 없으면 null.
 *                 사진을 보냈는데 null이면 사진 저장에 실패하고 기록만 저장된 것이다
 */
public record ObservationResponse(
        Long id,
        LocalDate observedDate,
        SkyType type,
        String typeLabel,
        BigDecimal latitude,
        BigDecimal longitude,
        int predictedScore,
        List<Factor> factors,
        Rating rating,
        String ratingLabel,
        Boolean hit,
        String memo,
        String placeName,
        String kakaoPlaceId,
        boolean fromRecommendation,
        boolean fromNotification,
        String photoUrl,
        LocalDateTime createdAt
) {

    public static ObservationResponse of(Observation o, List<Factor> factors) {
        String photoUrl = o.getPhotoPath() != null ? "/api/observations/" + o.getId() + "/photo" : null;
        return new ObservationResponse(
                o.getId(), o.getObservedDate(), o.getType(), o.getType().label(),
                o.getLatitude(), o.getLongitude(), o.getPredictedScore(), factors,
                o.getRating(), o.getRating().label(), o.isHit(),
                o.getMemo(), o.getPlaceName(), o.getKakaoPlaceId(),
                o.isFromRecommendation(), o.isFromNotification(),
                photoUrl, o.getCreatedAt());
    }
}
