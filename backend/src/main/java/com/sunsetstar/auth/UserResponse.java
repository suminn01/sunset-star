package com.sunsetstar.auth;

/**
 * 회원 정보 응답. 회원가입 결과와 내 정보(GET /api/auth/me)에 같이 쓴다.
 *
 * 예: {"email": "sumin@example.com", "nickname": "노을러"}
 * 비밀번호(암호화 값 포함)는 절대 내보내지 않는다.
 */
public record UserResponse(String email, String nickname) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getEmail(), user.getNickname());
    }
}
