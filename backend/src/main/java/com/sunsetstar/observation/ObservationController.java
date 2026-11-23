package com.sunsetstar.observation;

import com.sunsetstar.auth.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관측 기록 API (기능명세서 F7). 모두 로그인이 필요하다 (SecurityConfig의 /api/observations/**).
 *
 * POST /api/observations  기록 등록 (multipart/form-data: 글자 칸들 + photo 파일) → 201
 * (목록, 사진, 통계는 다음 커밋에서 추가)
 */
@Tag(name = "관측 기록", description = "직접 본 하늘 기록과 예측 적중률 (로그인 필요)")
@RestController
@RequestMapping("/api/observations")
public class ObservationController {

    private final ObservationService observationService;

    public ObservationController(ObservationService observationService) {
        this.observationService = observationService;
    }

    /**
     * multipart/form-data: 글자와 파일을 한 번에 보내는 방식. 사진 업로드는 보통 이렇게 보낸다.
     * @ModelAttribute: 글자 칸들(type, rating 등)과 사진(photo)을 ObservationCreateRequest 하나로 모은다.
     * 사진 칸을 따로 받으면 Swagger 화면에 사진 칸이 안 보여서 한곳에 모았다. 사진 없이 기록해도 된다
     */
    @Operation(summary = "관측 기록 등록",
            description = "평가·메모·사진과 함께, 기록하는 순간 화면에 보이던 예측 점수를 저장한다. 사진은 JPG·PNG·WEBP 10MB까지")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ObservationResponse create(@AuthenticationPrincipal LoginUser user,
                                      @Valid @ModelAttribute ObservationCreateRequest request) {
        return observationService.create(user, request);
    }
}
