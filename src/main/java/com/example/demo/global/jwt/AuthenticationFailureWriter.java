package com.example.demo.global.jwt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.example.demo.global.exception.ErrorCode;
import com.example.demo.global.response.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 인증·인가 실패 응답을 우리 형식으로 쓴다.
 *
 * <p>필터 단계의 실패는 @RestControllerAdvice 를 타지 않는다. 그대로 두면 스프링 기본
 * 오류 본문이 나가서 응답 형식이 두 가지가 된다.
 */
@Component
@RequiredArgsConstructor
public class AuthenticationFailureWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** 토큰이 아예 없거나 인가 단계에서 걸렸을 때. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        write(response, ErrorCode.UNAUTHORIZED);
    }

    /** 인증은 됐지만 권한이 없을 때. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(response, ErrorCode.FORBIDDEN);
    }

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiErrorResponse.of(errorCode)));
    }
}
