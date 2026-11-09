package com.sunsetstar.global;

/**
 * 우리 앱이 직접 내는 에러. 던지면 GlobalExceptionHandler가 {code, message} 응답으로 바꾼다.
 *
 * 예: throw new ApiException(ErrorCode.WEATHER_UNAVAILABLE);
 *     throw new ApiException(ErrorCode.INVALID_PARAMETER, "예보가 있는 날짜가 아니에요");
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    /** ErrorCode의 기본 안내 문장을 쓴다. */
    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.message());
    }

    /** 안내 문장을 직접 정한다. */
    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
