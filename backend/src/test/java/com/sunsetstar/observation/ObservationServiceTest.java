package com.sunsetstar.observation;

import com.sunsetstar.auth.LoginUser;
import com.sunsetstar.auth.User;
import com.sunsetstar.auth.UserRepository;
import com.sunsetstar.forecast.Factor;
import com.sunsetstar.forecast.SkyType;
import com.sunsetstar.global.ApiException;
import com.sunsetstar.global.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관측 기록 등록 규칙 테스트. DB와 사진 폴더 대신 Mockito 가짜를 쓴다.
 */
class ObservationServiceTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));
    private static final LoginUser SUMIN = new LoginUser(1L, "sumin@example.com");

    private ObservationRepository observationRepository;
    private UserRepository userRepository;
    private PhotoStorage photoStorage;
    private ObservationService service;

    @BeforeEach
    void setUp() {
        observationRepository = mock(ObservationRepository.class);
        userRepository = mock(UserRepository.class);
        photoStorage = mock(PhotoStorage.class);
        service = new ObservationService(observationRepository, userRepository, photoStorage, JsonMapper.builder().build());

        when(userRepository.getReferenceById(1L)).thenReturn(User.local("sumin@example.com", "$2a$hash", "노을러"));
        when(observationRepository.save(any(Observation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ObservationCreateRequest request(LocalDate date, String factors) {
        return request(date, factors, null);
    }

    private ObservationCreateRequest request(LocalDate date, String factors, MultipartFile photo) {
        return new ObservationCreateRequest(SkyType.SUNSET, date,
                new BigDecimal("37.56651234"), new BigDecimal("126.978"), 78, factors, Rating.GOOD,
                "  구름이 분홍색  ", "하늘공원", "8217406", true, null, photo);
    }

    private Observation saved() {
        ArgumentCaptor<Observation> captor = ArgumentCaptor.forClass(Observation.class);
        verify(observationRepository).save(captor.capture());
        return captor.getValue();
    }

    private ErrorCode errorOf(Runnable action) {
        try {
            action.run();
        } catch (ApiException e) {
            return e.errorCode();
        }
        throw new AssertionError("실패해야 해요");
    }

    @Test
    void 화면에_보이던_점수와_요소를_그대로_저장한다() {
        String factors = "[{\"name\":\"위쪽 구름\",\"points\":15,\"reason\":\"높은 구름이 적당해요\"}]";

        ObservationResponse response = service.create(SUMIN, request(TODAY, factors));

        Observation observation = saved();
        assertThat(observation.getPredictedScore()).isEqualTo(78);
        assertThat(response.factors()).containsExactly(new Factor("위쪽 구름", 15, "높은 구름이 적당해요"));
        assertThat(response.hit()).isTrue(); // 78점 + GOOD
        assertThat(response.ratingLabel()).isEqualTo("예뻤어요");
    }

    @Test
    void 좌표는_소수점_여섯째_자리로_메모는_앞뒤_공백을_빼고_저장한다() {
        service.create(SUMIN, request(TODAY, null));

        Observation observation = saved();
        assertThat(observation.getLatitude()).isEqualByComparingTo("37.566512");
        assertThat(observation.getMemo()).isEqualTo("구름이 분홍색");
        assertThat(observation.isFromRecommendation()).isTrue();
        assertThat(observation.isFromNotification()).isFalse(); // 안 보내면 false
    }

    @Test
    void 사진을_저장하면_파일_이름을_붙인다() {
        MockMultipartFile photo = new MockMultipartFile("photo", "sky.jpg", "image/jpeg", new byte[]{1});
        when(photoStorage.save(photo)).thenReturn("3f2a1b4c-0000-0000-0000-000000000000.jpg");

        service.create(SUMIN, request(TODAY, null, photo));

        assertThat(saved().getPhotoPath()).isEqualTo("3f2a1b4c-0000-0000-0000-000000000000.jpg");
    }

    @Test
    void 사진_저장에_실패해도_기록은_저장한다() {
        MockMultipartFile photo = new MockMultipartFile("photo", "sky.jpg", "image/jpeg", new byte[]{1});
        when(photoStorage.save(photo)).thenReturn(null);

        ObservationResponse response = service.create(SUMIN, request(TODAY, null, photo));

        assertThat(saved().getPhotoPath()).isNull();
        assertThat(response.photoUrl()).isNull();
    }

    @Test
    void 어제와_7일_전은_기록할_수_있다() {
        service.create(SUMIN, request(TODAY.minusDays(1), null));
        service.create(SUMIN, request(TODAY.minusDays(ObservationRules.MAX_DAYS_AGO), null));
    }

    @Test
    void 미래나_8일_전은_400() {
        assertThat(errorOf(() -> service.create(SUMIN, request(TODAY.plusDays(1), null))))
                .isEqualTo(ErrorCode.INVALID_PARAMETER);
        assertThat(errorOf(() -> service.create(SUMIN, request(TODAY.minusDays(8), null))))
                .isEqualTo(ErrorCode.INVALID_PARAMETER);
        verify(observationRepository, never()).save(any());
    }

    @Test
    void 요소_JSON_형식이_틀리면_400이고_사진도_저장하지_않는다() {
        MockMultipartFile photo = new MockMultipartFile("photo", "sky.jpg", "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> service.create(SUMIN, request(TODAY, "위쪽 구름 +15", photo)))
                .isInstanceOf(ApiException.class)
                .hasMessage("요소(factors) 형식이 올바르지 않아요");
        verify(photoStorage, never()).save(any());
    }
}
