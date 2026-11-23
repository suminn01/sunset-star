package com.sunsetstar.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입·로그인 API (기능명세서 F9).
 *
 * POST /api/auth/signup  회원가입 → 201 {email, nickname}
 * (로그인, 내 정보, 로그아웃은 다음 커밋에서 추가)
 */
@Tag(name = "회원", description = "회원가입, 로그인, 로그아웃, 내 정보")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * @RequestBody: 요청 본문의 JSON을 SignupRequest로 바꾼다
     * @Valid: SignupRequest 칸마다 붙은 규칙(@Email 등)을 검사한다. 어기면 400
     * @ResponseStatus(CREATED): 새로 만들었다는 뜻의 201로 답한다
     */
    @Operation(summary = "회원가입",
            description = "이메일·비밀번호·닉네임으로 가입한다. 가입만 하고 로그인은 따로 한다. 이메일이 이미 있으면 409")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }
}
