package com.sunsetstar.observation;

import com.sunsetstar.auth.LoginUser;
import com.sunsetstar.auth.User;
import com.sunsetstar.auth.UserRepository;
import com.sunsetstar.forecast.Factor;
import com.sunsetstar.global.ApiException;
import com.sunsetstar.global.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 관측 기록 규칙 (기능명세서 F7).
 *
 * 등록 순서
 * 1. 날짜 검사: 오늘부터 7일 전까지 (미래 X)
 * 2. 요소(factors) JSON을 읽어 본다. 형식이 틀리면 400
 * 3. 사진 저장 (형식·크기가 틀리면 400, 디스크 실패면 사진 없이 계속)
 * 4. 세션의 사용자 id로 기록 저장. 좌표는 소수점 여섯째 자리까지 (DB 칸 DECIMAL(9,6))
 *
 * 조회: 목록·사진·통계 모두 내 기록만. 남의 기록 사진을 달라고 하면 403 FORBIDDEN
 */
@Service
public class ObservationService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final TypeReference<List<Factor>> FACTOR_LIST = new TypeReference<>() {
    };

    private final ObservationRepository observationRepository;
    private final UserRepository userRepository;
    private final PhotoStorage photoStorage;
    private final JsonMapper jsonMapper;

    public ObservationService(ObservationRepository observationRepository, UserRepository userRepository,
                              PhotoStorage photoStorage, JsonMapper jsonMapper) {
        this.observationRepository = observationRepository;
        this.userRepository = userRepository;
        this.photoStorage = photoStorage;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public ObservationResponse create(LoginUser loginUser, ObservationCreateRequest request) {
        checkDate(request.observedDate());
        List<Factor> factors = readFactors(request.factors());
        String photoPath = photoStorage.save(request.photo());

        // getReferenceById: DB를 다시 읽지 않고 "id가 이 회원"이라는 표시만 만든다. user_id 칸을 채우는 데는 이걸로 충분하다
        User user = userRepository.getReferenceById(loginUser.id());
        Observation observation = new Observation(
                user, request.observedDate(), request.type(),
                round(request.latitude()), round(request.longitude()),
                request.predictedScore(), factors.isEmpty() ? null : jsonMapper.writeValueAsString(factors),
                request.rating(), blankToNull(request.memo()),
                blankToNull(request.placeName()), blankToNull(request.kakaoPlaceId()),
                Boolean.TRUE.equals(request.fromRecommendation()), Boolean.TRUE.equals(request.fromNotification()));
        observation.attachPhoto(photoPath);

        return ObservationResponse.of(observationRepository.save(observation), factors);
    }

    /** 내 기록 목록, 최신순. page는 0부터 (0 = 첫 페이지) */
    @Transactional(readOnly = true)
    public ObservationPageResponse list(LoginUser loginUser, int page) {
        Page<Observation> result = observationRepository.findByUserIdOrderByObservedDateDescIdDesc(
                loginUser.id(), PageRequest.of(page, ObservationRules.PAGE_SIZE));
        return new ObservationPageResponse(
                result.getContent().stream()
                        .map(observation -> ObservationResponse.of(observation, factorsOf(observation)))
                        .toList(),
                page, result.getTotalPages(), result.getTotalElements(), result.hasNext());
    }

    /**
     * 기록 사진. 없는 기록 404, 남의 기록 403, 사진이 없거나 파일이 사라졌으면 404
     */
    @Transactional(readOnly = true)
    public PhotoStorage.StoredPhoto photo(LoginUser loginUser, Long observationId) {
        Observation observation = observationRepository.findById(observationId)
                .orElseThrow(() -> new ApiException(ErrorCode.OBSERVATION_NOT_FOUND));
        if (!observation.isOwnedBy(loginUser.id())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "다른 사람의 기록은 볼 수 없어요");
        }
        return photoStorage.find(observation.getPhotoPath())
                .orElseThrow(() -> new ApiException(ErrorCode.PHOTO_NOT_FOUND));
    }

    private void checkDate(LocalDate observedDate) {
        LocalDate today = LocalDate.now(SEOUL);
        if (observedDate.isAfter(today)) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER, "아직 오지 않은 날은 기록할 수 없어요");
        }
        if (observedDate.isBefore(today.minusDays(ObservationRules.MAX_DAYS_AGO))) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER,
                    ObservationRules.MAX_DAYS_AGO + "일 전까지만 기록할 수 있어요");
        }
    }

    /** 요소 JSON 글자 → 목록. 비어 있으면 빈 목록 */
    List<Factor> readFactors(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Factor> factors = jsonMapper.readValue(json, FACTOR_LIST);
            return factors == null ? List.of() : factors;
        } catch (JacksonException e) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER, "요소(factors) 형식이 올바르지 않아요");
        }
    }

    /** DB에서 꺼낸 요소 JSON → 목록. 저장할 때 검사했으니 여기서는 실패하지 않는다 */
    List<Factor> factorsOf(Observation observation) {
        return readFactors(observation.getFactors());
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
