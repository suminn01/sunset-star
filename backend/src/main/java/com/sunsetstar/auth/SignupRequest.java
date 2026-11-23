package com.sunsetstar.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 때 프런트가 보내는 JSON.
 *
 * 예: {"email": "sumin@example.com", "password": "sunset2026", "nickname": "노을러"}
 *
 * 칸마다 붙은 @Email, @Size 같은 규칙은 컨트롤러의 @Valid가 검사한다.
 * 하나라도 어기면 서비스까지 가지 않고 400 {code: INVALID_PARAMETER, message: 규칙 문장}이 나간다.
 * 비밀번호 확인 칸은 화면에서만 비교하고 서버로 보내지 않는다 (기능명세서 F9).
 */
public record SignupRequest(
        @Schema(example = "sumin@example.com")
        @NotBlank(message = AuthRules.EMAIL_REQUIRED)
        @Email(message = AuthRules.EMAIL_INVALID)
        @Size(max = AuthRules.EMAIL_MAX, message = AuthRules.EMAIL_TOO_LONG)
        String email,

        @Schema(example = "sunset2026")
        @NotNull(message = AuthRules.PASSWORD_INVALID)
        @Size(min = AuthRules.PASSWORD_MIN, max = AuthRules.PASSWORD_MAX, message = AuthRules.PASSWORD_INVALID)
        @Pattern(regexp = AuthRules.PASSWORD_PATTERN, message = AuthRules.PASSWORD_INVALID)
        String password,

        @Schema(example = "노을러")
        @NotBlank(message = AuthRules.NICKNAME_INVALID)
        @Size(min = AuthRules.NICKNAME_MIN, max = AuthRules.NICKNAME_MAX, message = AuthRules.NICKNAME_INVALID)
        String nickname
) {
}
