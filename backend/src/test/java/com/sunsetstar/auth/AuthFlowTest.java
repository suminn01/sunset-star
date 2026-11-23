package com.sunsetstar.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회원가입 → 로그인 → 내 정보 → 로그아웃을 진짜 서버와 진짜 MySQL로 한 번에 확인한다 (기능명세서 F9 완료 기준).
 *
 * - RANDOM_PORT: 테스트용 서버를 빈 포트로 하나 더 켠다 (지금 켜 둔 8080 서버와 겹치지 않는다)
 * - CookieManager: 브라우저처럼 받은 JSESSIONID 쿠키를 기억했다가 다음 요청에 붙인다
 * - 매번 새 이메일로 가입하고, 끝나면 그 회원을 지운다
 *
 * MySQL(MySQL96 서비스)이 켜져 있어야 통과한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowTest {

    private static final String PASSWORD = "sunset2026";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private UserRepository userRepository;

    private HttpClient client;
    private String email;

    @BeforeEach
    void setUp() {
        client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();
        email = "test-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    @AfterEach
    void cleanUp() {
        userRepository.findByEmail(email).ifPresent(userRepository::delete);
    }

    private HttpResponse<String> post(String path, String json) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String signupJson() {
        return """
                {"email": "%s", "password": "%s", "nickname": "노을러"}
                """.formatted(email, PASSWORD);
    }

    private String loginJson(String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    @Test
    void 가입_로그인_내정보_로그아웃() throws Exception {
        // 1. 가입 → 201, 같은 이메일로 또 가입 → 409
        assertThat(post("/api/auth/signup", signupJson()).statusCode()).isEqualTo(201);
        HttpResponse<String> duplicate = post("/api/auth/signup", signupJson());
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("DUPLICATE_EMAIL");

        // 2. DB에는 암호화된 비밀번호가 들어 있다
        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo(PASSWORD).startsWith("$2a$");

        // 3. 로그인 전: 내 정보 401
        HttpResponse<String> beforeLogin = get("/api/auth/me");
        assertThat(beforeLogin.statusCode()).isEqualTo(401);
        assertThat(beforeLogin.body()).contains("UNAUTHORIZED");

        // 4. 비밀번호가 틀리면 401 LOGIN_FAILED
        HttpResponse<String> wrong = post("/api/auth/login", loginJson("wrong2026"));
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(wrong.body()).contains("LOGIN_FAILED");

        // 5. 로그인 → 200 + JSESSIONID 쿠키
        HttpResponse<String> login = post("/api/auth/login", loginJson(PASSWORD));
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().allValues("Set-Cookie")).anyMatch(cookie -> cookie.startsWith("JSESSIONID="));

        // 6. 쿠키가 같이 가서 내 정보 200
        HttpResponse<String> me = get("/api/auth/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains(email).contains("노을러").doesNotContain("password");

        // 7. 로그아웃 → 204, 그 뒤 내 정보 다시 401
        assertThat(post("/api/auth/logout", "").statusCode()).isEqualTo(204);
        assertThat(get("/api/auth/me").statusCode()).isEqualTo(401);
    }

    @Test
    void 로그인하지_않으면_관측_기록_API는_401() throws Exception {
        // 관측 기록 API는 9단계에서 만들지만, 문지기는 지금도 주소를 보고 막는다
        HttpResponse<String> response = get("/api/observations");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("UNAUTHORIZED");
    }

    @Test
    void 로그인_없이도_예보_같은_공개_API는_막히지_않는다() throws Exception {
        // 좌표가 틀린 요청이라 400이지만, 401이 아니라는 것은 문지기를 통과했다는 뜻이다
        assertThat(get("/api/forecast?lat=999&lon=0").statusCode()).isEqualTo(400);
    }
}
