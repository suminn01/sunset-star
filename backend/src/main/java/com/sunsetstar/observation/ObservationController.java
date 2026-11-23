package com.sunsetstar.observation;

import com.sunsetstar.auth.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Min;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.beans.PropertyEditorSupport;
import java.time.Duration;

/**
 * 관측 기록 API (기능명세서 F7). 모두 로그인이 필요하다 (SecurityConfig의 /api/observations/**).
 *
 * | 주소                                 | 하는 일                                         |
 * | POST /api/observations               | 기록 등록 (multipart/form-data) → 201             |
 * | GET  /api/observations?page=0        | 내 기록 목록, 최신순 20개씩                         |
 * | GET  /api/observations/{id}/photo    | 기록 사진 (남의 기록이면 403)                       |
 * | GET  /api/observations/stats         | 내 예측 적중률, 추천 장소 만족도, 알림 후 관측 수       |
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
     * 사진 칸이 파일이 아니라 빈 글자(photo=)로 오면 "사진 없음"으로 본다.
     * Swagger는 파일을 안 고르면 photo= 를 빈 글자로 보내는데, 그대로 두면 글자를 파일로 바꾸지 못해 400이 난다.
     */
    @InitBinder
    void allowEmptyPhoto(WebDataBinder binder) {
        binder.registerCustomEditor(MultipartFile.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(null);
            }
        });
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

    @Operation(summary = "내 기록 목록", description = "최신순 20개씩. page는 0부터 시작한다 (0 = 첫 페이지)")
    @GetMapping
    public ObservationPageResponse list(@AuthenticationPrincipal LoginUser user,
                                        @Parameter(description = "페이지 번호 (0부터)", example = "0")
                                        @RequestParam(defaultValue = "0")
                                        @Min(value = 0, message = "페이지(page)는 0 이상이어야 해요") int page) {
        return observationService.list(user, page);
    }

    @Operation(summary = "내 관측 통계",
            description = "예측 적중률(60점 이상+예뻤어요 / 60점 미만+별로였어요, 그냥 그래요는 뺌), 추천 장소 만족도, 알림 후 관측 수. 기록이 없으면 비율은 null")
    @GetMapping("/stats")
    public ObservationStatsResponse stats(@AuthenticationPrincipal LoginUser user) {
        return observationService.stats(user);
    }

    /**
     * 사진 파일을 그대로 돌려준다. 프런트는 <img src="/api/observations/1/photo">로 보여 줄 수 있다 (같은 주소라 쿠키가 같이 간다).
     * Cache-Control private: 내 브라우저에만 하루 동안 저장해 두고, 중간 서버(공용 캐시)에는 남기지 않는다
     */
    @Operation(summary = "기록 사진", description = "내 기록의 사진. 남의 기록이면 403, 사진이 없으면 404")
    @GetMapping("/{id}/photo")
    public ResponseEntity<Resource> photo(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        PhotoStorage.StoredPhoto photo = observationService.photo(user, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(new FileSystemResource(photo.path()));
    }
}
