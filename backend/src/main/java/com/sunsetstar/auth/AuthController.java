package com.sunsetstar.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입·로그인 API (기능명세서 F9).
 *
 * | 주소                    | 로그인 | 성공                      | 실패                          |
 * | POST /api/auth/signup   | 불필요 | 201 {email, nickname}     | 400 규칙 위반, 409 이메일 중복  |
 * | POST /api/auth/login    | 불필요 | 200 {email, nickname} + 쿠키 | 401 LOGIN_FAILED           |
 * | GET  /api/auth/me       | 필요   | 200 {email, nickname}     | 401 UNAUTHORIZED              |
 * | POST /api/auth/logout   | 필요   | 204 (내용 없음) + 쿠키 삭제 | 401 UNAUTHORIZED              |
 *
 * 로그인 필요 여부는 SecurityConfig의 LOGIN_REQUIRED에서 정한다.
 */
@Tag(name = "회원", description = "회원가입, 로그인, 로그아웃, 내 정보")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginSession loginSession;

    public AuthController(AuthService authService, LoginSession loginSession) {
        this.authService = authService;
        this.loginSession = loginSession;
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

    @Operation(summary = "로그인",
            description = "맞으면 세션을 만들고 JSESSIONID 쿠키를 준다. 틀리면 어느 쪽이 틀렸는지 말하지 않고 401")
    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        LoginUser user = authService.authenticate(request);
        loginSession.login(user, httpRequest, httpResponse);
        return authService.me(user.id());
    }

    /** @AuthenticationPrincipal: 세션에 들어 있는 로그인한 사람(LoginUser)을 꺼내 준다 */
    @Operation(summary = "내 정보", description = "로그인했으면 이메일·닉네임, 아니면 401. 앱을 켤 때 로그인 상태 확인에 쓴다")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal LoginUser user) {
        return authService.me(user.id());
    }

    @Operation(summary = "로그아웃", description = "세션을 버리고 쿠키를 지운다")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        loginSession.logout(httpRequest, httpResponse);
    }
}
