package com.sunsetstar.auth;

/**
 * 어떤 방법으로 가입한 회원인지 (users.provider 칸).
 *
 * LOCAL: 이메일·비밀번호로 가입 (8단계에서 만드는 방식)
 * KAKAO: 카카오 로그인으로 가입 (도전 기능, 나중에 추가). 비밀번호 없이 provider_id로 구분한다
 */
public enum Provider {
    LOCAL,
    KAKAO
}
