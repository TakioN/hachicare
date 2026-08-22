package com.example.demo.global.session;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 로그인 없는 서비스에서 "내가 만든 작업"을 구분하기 위한 익명 세션.
 *
 * <p>HttpOnly 쿠키로만 주고받는다. 값 자체가 곧 소유권이므로 JS에서 읽히면 안 된다.
 */
@Component
public class AnonymousSessionManager {

    public static final String COOKIE_NAME = "session_key";

    private static final Duration TTL = Duration.ofDays(7);

    private final boolean secureCookie;

    public AnonymousSessionManager(@Value("${app.session.cookie-secure:true}") boolean secureCookie) {
        this.secureCookie = secureCookie;
    }

    /** 쿠키가 있으면 그 값을, 없으면 새로 발급해 응답에 심고 그 값을 돌려준다. */
    public String resolveOrIssue(HttpServletRequest request, HttpServletResponse response) {
        return find(request).orElseGet(() -> issue(response));
    }

    /** 조회 전용. 세션이 없으면 발급하지 않는다. */
    public Optional<String> find(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private String issue(HttpServletResponse response) {
        String sessionKey = UUID.randomUUID().toString().replace("-", "");

        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, sessionKey)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(TTL)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return sessionKey;
    }
}
