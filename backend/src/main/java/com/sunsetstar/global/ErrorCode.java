package com.sunsetstar.global;

import org.springframework.http.HttpStatus;

/**
 * 에러 종류 목록. 에러 응답 {code, message}의 code가 이 이름이다 (기능명세서 F8).
 *
 * 프런트는 code를 보고 화면을 정한다.
 * 예: WEATHER_UNAVAILABLE → "하늘 정보를 못 가져왔어요" + 다시 시도 버튼, INVALID_PARAMETER → 설정 화면으로 안내
 *
 * 로그인(8단계), 기록(9단계)을 만들 때 UNAUTHORIZED(401), FORBIDDEN(403), DUPLICATE_EMAIL(409)을 여기에 더한다.
 */
public enum ErrorCode {

    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않아요"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "없는 주소예요"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식이에요"),
    WEATHER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "하늘 정보를 못 가져왔어요. 잠시 후 다시 시도해 주세요"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /** HTTP 상태 코드 (예: 400, 503) */
    public HttpStatus status() {
        return status;
    }

    /** 기본 안내 문장. 더 자세한 문장이 있으면 ApiException에서 바꿔 쓴다 */
    public String message() {
        return message;
    }
}
