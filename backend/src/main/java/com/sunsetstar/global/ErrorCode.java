package com.sunsetstar.global;

import org.springframework.http.HttpStatus;

/**
 * 에러 종류 목록. 에러 응답 {code, message}의 code가 이 이름이다 (기능명세서 F8).
 *
 * 프런트는 code를 보고 화면을 정한다.
 * 예: WEATHER_UNAVAILABLE → "하늘 정보를 못 가져왔어요" + 다시 시도 버튼, INVALID_PARAMETER → 설정 화면으로 안내,
 *     LOCATION_UNAVAILABLE → 위치 검색창에 안내 문구
 *
 * 로그인이 필요한 API를 로그인 없이 부르면 UNAUTHORIZED(401), 남의 것을 보려 하면 FORBIDDEN(403)이다.
 * 이 둘은 컨트롤러까지 가기 전에 SecurityErrorHandler가 응답한다.
 */
public enum ErrorCode {

    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않아요"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "없는 주소예요"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식이에요"),
    WEATHER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "하늘 정보를 못 가져왔어요. 잠시 후 다시 시도해 주세요"),
    LOCATION_NOT_FOUND(HttpStatus.NOT_FOUND, "이 위치의 지역 이름을 찾지 못했어요"),
    LOCATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 위치를 찾지 못했어요. 잠시 후 다시 시도해 주세요"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "볼 수 있는 권한이 없어요"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일이에요"),
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
