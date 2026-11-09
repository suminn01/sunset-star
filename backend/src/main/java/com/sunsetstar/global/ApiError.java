package com.sunsetstar.global;

/**
 * 모든 에러 응답의 모양 (CLAUDE.md 규칙, 기능명세서 F8).
 *
 * 예: {"code": "INVALID_PARAMETER", "message": "위도(lat)는 -90 ~ 90 사이여야 해요"}
 *
 * @param code    에러 종류 (ErrorCode 이름). 프런트가 화면을 정할 때 쓴다
 * @param message 사용자에게 보여 줄 수 있는 안내 문장
 */
public record ApiError(String code, String message) {

    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code.name(), message);
    }
}
