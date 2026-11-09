package com.sunsetstar.global;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Objects;

/**
 * 서버 어디서 에러가 나든 여기서 잡아서 {code, message} 모양으로 바꾼다 (기능명세서 F8).
 *
 * | 상황                                   | 상태 | code                |
 * | 우리가 던진 ApiException              | 그 코드대로                  |
 * | 위도·경도 범위 밖 (@DecimalMin 등)      | 400  | INVALID_PARAMETER   |
 * | 필요한 값이 빠짐 (lat 없음)             | 400  | INVALID_PARAMETER   |
 * | 형식이 틀림 (lat=abc, light=ABC)        | 400  | INVALID_PARAMETER   |
 * | 없는 주소, 지원하지 않는 방식 등 Spring 에러 | 404, 405 등 | NOT_FOUND 등   |
 * | 그 밖의 예상 못 한 에러                 | 500  | INTERNAL_ERROR      |
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 우리 앱이 직접 던진 에러 */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException e) {
        return respond(e.errorCode(), e.getMessage());
    }

    /** @DecimalMin 같은 검사에 걸렸을 때. 검사에 적어 둔 문장을 그대로 보여 준다. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleValidation(HandlerMethodValidationException e) {
        String message = e.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(ErrorCode.INVALID_PARAMETER.message());
        return respond(ErrorCode.INVALID_PARAMETER, message);
    }

    /** 꼭 필요한 값이 빠졌을 때. 예: lat 없이 요청 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException e) {
        return respond(ErrorCode.INVALID_PARAMETER, "필요한 값이 빠졌어요: " + e.getParameterName());
    }

    /** 값의 형식이 틀렸을 때. 예: lat=abc, light=ABC, date=11월9일 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return respond(ErrorCode.INVALID_PARAMETER, "값의 형식이 올바르지 않아요: " + e.getName());
    }

    /**
     * 위에서 못 잡은 나머지.
     * Spring이 상태 코드를 정해 둔 에러(없는 주소 404, 지원하지 않는 방식 405 등)는 그 상태를 따르고,
     * 정말 예상 못 한 에러는 기록을 남기고 500으로 답한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOthers(Exception e) {
        if (e instanceof ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            ErrorCode code = switch (status.value()) {
                case 404 -> ErrorCode.NOT_FOUND;
                case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
                default -> status.is4xxClientError() ? ErrorCode.INVALID_PARAMETER : ErrorCode.INTERNAL_ERROR;
            };
            return ResponseEntity.status(status).body(ApiError.of(code, code.message()));
        }
        log.error("처리하지 못한 에러", e);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message());
    }

    private ResponseEntity<ApiError> respond(ErrorCode code, String message) {
        return ResponseEntity.status(code.status()).body(ApiError.of(code, message));
    }
}
