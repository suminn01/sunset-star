package com.sunsetstar.observation;

import com.sunsetstar.forecast.SkyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 관측 기록 등록 때 프런트가 보내는 내용 (multipart/form-data의 글자 칸들 + 사진 photo 칸).
 *
 * 프런트에서는 FormData에 칸을 하나씩 넣어 보낸다.
 *   form.append("type", "SUNSET"); form.append("rating", "GOOD"); form.append("photo", 파일) ...
 *
 * 예측 점수(predictedScore)와 요소(factors)는 기록하는 순간 화면에 보이던 값을 그대로 보낸다.
 * 추천 장소에서 들어왔으면 그 장소의 점수, 아니면 현재 위치 점수다.
 *
 * @param factors 요소별 가감점 JSON 글자. 예: [{"name":"위쪽 구름","points":15,"reason":"..."}]. 없어도 된다
 */
public record ObservationCreateRequest(
        @Schema(description = "SUNSET(노을) / STAR(별)", example = "SUNSET")
        @NotNull(message = "종류(type)를 골라 주세요")
        SkyType type,

        @Schema(description = "관측한 날 yyyy-MM-dd. 오늘부터 7일 전까지", example = "2026-11-23")
        @NotNull(message = "관측한 날(observedDate)을 입력해 주세요")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate observedDate,

        @Schema(example = "37.5665")
        @NotNull(message = ObservationRules.LAT_MESSAGE)
        @DecimalMin(value = "-90", message = ObservationRules.LAT_MESSAGE) @DecimalMax(value = "90", message = ObservationRules.LAT_MESSAGE)
        BigDecimal latitude,

        @Schema(example = "126.978")
        @NotNull(message = ObservationRules.LON_MESSAGE)
        @DecimalMin(value = "-180", message = ObservationRules.LON_MESSAGE) @DecimalMax(value = "180", message = ObservationRules.LON_MESSAGE)
        BigDecimal longitude,

        @Schema(description = "기록하는 순간 화면에 보이던 예측 점수", example = "78")
        @NotNull(message = ObservationRules.SCORE_MESSAGE)
        @Min(value = 0, message = ObservationRules.SCORE_MESSAGE) @Max(value = 100, message = ObservationRules.SCORE_MESSAGE)
        Integer predictedScore,

        @Schema(description = "요소별 가감점 JSON (선택)",
                example = "[{\"name\":\"위쪽 구름\",\"points\":15,\"reason\":\"높은 구름이 적당해요\"}]")
        @Size(max = ObservationRules.FACTORS_MAX, message = "요소(factors)가 너무 길어요")
        String factors,

        @Schema(description = "GOOD(예뻤어요) / SOSO(그냥 그래요) / BAD(별로였어요)", example = "GOOD")
        @NotNull(message = "평가(rating)를 골라 주세요")
        Rating rating,

        @Schema(description = "한 줄 메모 (선택, 200자)", example = "구름이 분홍색으로 물들었어요")
        @Size(max = ObservationRules.MEMO_MAX, message = "메모는 200자 이하로 써 주세요")
        String memo,

        @Schema(description = "관측한 장소 이름 (선택)", example = "하늘공원")
        @Size(max = ObservationRules.PLACE_NAME_MAX, message = "장소 이름은 100자 이하로 써 주세요")
        String placeName,

        @Schema(description = "추천 장소의 카카오 장소 ID (선택)", example = "8217406")
        @Size(max = ObservationRules.KAKAO_PLACE_ID_MAX, message = "카카오 장소 ID가 너무 길어요")
        String kakaoPlaceId,

        @Schema(description = "근처 장소 추천(F5)에서 고른 장소에서 봤는지", example = "false")
        Boolean fromRecommendation,

        @Schema(description = "알림을 눌러 들어와 기록했는지", example = "false")
        Boolean fromNotification,

        @Schema(description = "사진 (선택). JPG·PNG·WEBP, 10MB까지", type = "string", format = "binary")
        MultipartFile photo
) {
}
