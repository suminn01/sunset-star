package com.sunsetstar.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 때 프런트가 보내는 JSON.
 *
 * 예: {"email": "sumin@example.com", "password": "sunset2026"}
 * 로그인에서는 형식 규칙(8자 이상 등)을 다시 검사하지 않는다. 비어 있는지만 보고, 맞는지는 DB와 비교한다.
 */
public record LoginRequest(
        @Schema(example = "sumin@example.com")
        @NotBlank(message = AuthRules.LOGIN_REQUIRED)
        String email,

        @Schema(example = "sunset2026")
        @NotBlank(message = AuthRules.LOGIN_REQUIRED)
        String password
) {
}
