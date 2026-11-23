package com.sunsetstar.global;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security가 요청을 막았을 때 {code, message} 모양으로 답한다.
 *
 * Security는 컨트롤러보다 앞에서 동작해서 GlobalExceptionHandler가 잡지 못한다. 그래서 여기서 직접 응답을 쓴다.
 * - 로그인 안 하고 로그인 필요한 API를 부름 → 401 {"code":"UNAUTHORIZED","message":"로그인이 필요해요"}
 * - 로그인은 했지만 권한이 없음            → 403 {"code":"FORBIDDEN", ...}
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public SecurityErrorHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** 로그인이 안 된 경우 (401) */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, ErrorCode.UNAUTHORIZED);
    }

    /** 로그인은 했지만 권한이 없는 경우 (403) */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(ApiError.of(code, code.message())));
    }
}
