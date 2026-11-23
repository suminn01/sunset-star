package com.sunsetstar.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 세션에 로그인 상태를 넣고 빼는 일을 맡는다 (기능명세서 F9, 세션 + JSESSIONID 쿠키).
 *
 * 세션이란? 서버가 "이 브라우저는 수민님" 하고 기억해 두는 메모장이다.
 * 서버는 메모장 번호를 JSESSIONID 쿠키로 브라우저에 주고, 브라우저는 다음 요청부터 이 쿠키를 같이 보낸다.
 * 그래서 새로고침해도 로그인이 유지된다.
 *
 * - login: 세션 번호를 새로 바꾸고(남이 미리 심어 둔 번호를 쓰지 못하게), 세션에 LoginUser를 넣는다
 * - logout: 세션을 버리고 JSESSIONID 쿠키를 지운다
 */
@Component
public class LoginSession {

    static final String SESSION_COOKIE = "JSESSIONID";

    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
    private final CookieClearingLogoutHandler cookieClearer = new CookieClearingLogoutHandler(SESSION_COOKIE);

    public void login(LoginUser user, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = contextHolder.getContext().getAuthentication();
        logoutHandler.logout(request, response, authentication);
        cookieClearer.logout(request, response, authentication);
    }
}
