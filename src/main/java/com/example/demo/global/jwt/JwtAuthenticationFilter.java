package com.example.demo.global.jwt;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.demo.global.exception.CustomException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Authorization 헤더의 Bearer 토큰으로 인증 주체를 세운다.
 *
 * <p>principal은 사용자 공개 식별자 문자열이다. 컨트롤러는 이 값을 그대로
 * 처방전 작업의 ownerKey로 쓴다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final AuthenticationFailureWriter failureWriter;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String token = bearerToken(request);
        if (token == null) {
            // 토큰이 없는 것 자체는 오류가 아니다. 공개 경로일 수 있으므로 인가 단계에 맡긴다.
            chain.doFilter(request, response);
            return;
        }

        try {
            authenticate(request, tokenProvider.parseSubject(token));
        } catch (CustomException e) {
            // 만료·위조는 여기서 끝낸다. 그대로 통과시키면 "로그인 필요"로 뭉개져
            // 프런트가 재발급해야 할지 재로그인해야 할지 알 수 없다.
            SecurityContextHolder.clearContext();
            failureWriter.write(response, e.getErrorCode());
            return;
        }

        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String userPublicId) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userPublicId, null, List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
