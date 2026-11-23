package com.sunsetstar.global;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정: 어떤 API를 누구나 쓰고, 어떤 API를 로그인한 사람만 쓰는지 정한다 (기능명세서 F9 접근 제어).
 *
 * 요청은 컨트롤러에 닿기 전에 여기 정한 규칙을 먼저 통과해야 한다.
 * - 로그인 필요 (LOGIN_REQUIRED): 내 정보, 로그아웃, 관측 기록, 알림 설정, 푸시 구독
 * - 그 밖의 모든 API: 누구나 (예보, 상세, 근처 장소, 위치 검색, 회원가입, 로그인, Swagger)
 *
 * Spring Security가 기본으로 켜 두는 것 중 우리 앱에 맞지 않는 것은 끈다.
 * - 로그인 화면(formLogin), 브라우저 팝업 로그인(httpBasic), 기본 로그아웃: 로그인은 우리가 만든 JSON API로 한다
 * - CSRF: 프런트가 같은 주소(Vite 프록시)로 JSON만 보내는 앱이라 끈다. 대신 세션 쿠키를 SameSite=Lax로 둔다
 */
@Configuration
public class SecurityConfig {

    /** 로그인해야 쓸 수 있는 주소. /** 는 그 아래 모든 주소를 뜻한다 */
    static final String[] LOGIN_REQUIRED = {
            "/api/auth/me",
            "/api/auth/logout",
            "/api/observations/**",
            "/api/notifications/**",
            "/api/push/subscriptions/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errorHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(LOGIN_REQUIRED).authenticated()
                        .anyRequest().permitAll())
                // 막혔을 때도 {code, message} 모양으로 답한다 (401 UNAUTHORIZED, 403 FORBIDDEN)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler));
        return http.build();
    }

    /** 비밀번호를 BCrypt로 암호화한다. 같은 비밀번호도 매번 다른 값이 나와서 DB를 봐도 원래 비밀번호를 알 수 없다 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
