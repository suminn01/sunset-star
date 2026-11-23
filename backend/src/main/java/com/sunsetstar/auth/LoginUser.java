package com.sunsetstar.auth;

import java.io.Serializable;

/**
 * 로그인한 사람 정보. 로그인에 성공하면 세션에 이 값이 들어간다.
 *
 * 로그인이 필요한 API에서 @AuthenticationPrincipal LoginUser user 라고 쓰면 꺼내 쓸 수 있다.
 * 9단계(관측 기록)에서 "누구의 기록인지" 정할 때 user.id()를 쓴다.
 * 세션에 저장되는 값이라 Serializable을 붙이고, 비밀번호는 넣지 않는다.
 */
public record LoginUser(Long id, String email) implements Serializable {

    public static LoginUser from(User user) {
        return new LoginUser(user.getId(), user.getEmail());
    }
}
