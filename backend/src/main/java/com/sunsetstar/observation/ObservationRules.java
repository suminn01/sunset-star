package com.sunsetstar.observation;

import java.util.Set;

/**
 * 관측 기록 기준 숫자 (기능명세서 F7).
 *
 * 적중 판정 (HIT_SCORE = 60)
 * | 예측 점수 | 평가        | 결과              |
 * | 60점 이상 | GOOD 예뻤어요 | 적중               |
 * | 60점 미만 | BAD 별로였어요 | 적중               |
 * | 60점 이상 | BAD          | 빗나감             |
 * | 60점 미만 | GOOD         | 빗나감             |
 * | 아무 점수 | SOSO 그냥 그래요 | 적중률 계산에서 뺌 |
 */
public final class ObservationRules {

    /** 이 점수 이상이면 "보기 좋다"고 예측한 것으로 본다 */
    public static final int HIT_SCORE = 60;

    public static final int MEMO_MAX = 200;
    public static final int PLACE_NAME_MAX = 100;
    public static final int KAKAO_PLACE_ID_MAX = 20;

    /** 며칠 전까지의 관측을 기록할 수 있는지. 미래 날짜는 안 된다 */
    public static final int MAX_DAYS_AGO = 7;

    /** 내 기록 목록 한 페이지에 보여 줄 개수 */
    public static final int PAGE_SIZE = 20;

    /** 사진 최대 크기 10MB. application.yml의 spring.servlet.multipart.max-file-size와 맞춘다 */
    public static final long PHOTO_MAX_BYTES = 10L * 1024 * 1024;
    /** 받는 사진 형식: JPG, PNG, WEBP (휴대폰 사진은 대부분 JPG로 올라온다) */
    public static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private ObservationRules() {
    }
}
