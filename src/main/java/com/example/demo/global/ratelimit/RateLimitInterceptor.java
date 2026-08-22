package com.example.demo.global.ratelimit;

import java.time.Duration;

import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.demo.global.exception.CustomException;
import com.example.demo.global.exception.ErrorCode;
import com.example.demo.global.ratelimit.RateLimitProperties.Bucket;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 분석 요청 한도.
 *
 * <p>사용자와 IP 두 기준으로 센다. 계정을 여러 개 만들면 사용자 한도는 우회되고,
 * IP만 쓰면 같은 공유망(행사장 와이파이 등)에 있는 사용자들이 서로의 한도를 잡아먹는다.
 * 그래서 사용자는 좁게, IP는 넉넉하게 잡는다.
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "rate:extraction:";

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.enabled()) {
            return true;
        }

        // 인가 전에 도는 인터셉터는 아니지만, 공개 경로에서 불릴 수도 있어 없을 때를 허용한다.
        currentUser().ifPresent(userPublicId ->
                check(KEY_PREFIX + "user:" + userPublicId, properties.session(), response));

        check(KEY_PREFIX + "ip:" + request.getRemoteAddr(), properties.ip(), response);

        return true;
    }

    private static Optional<String> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return Optional.of(authentication.getPrincipal())
                .filter(String.class::isInstance)
                .map(String.class::cast);
    }

    private void check(String key, Bucket bucket, HttpServletResponse response) {
        rateLimiter.exceeded(key, bucket.limit(), bucket.window()).ifPresent(retryAfter -> {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds(retryAfter)));
            throw new CustomException(ErrorCode.RATE_LIMIT_EXCEEDED);
        });
    }

    /** 0초를 돌려주면 즉시 재시도하게 되므로 최소 1초로 올린다. */
    private static long retryAfterSeconds(Duration retryAfter) {
        return Math.max(1, (long) Math.ceil(retryAfter.toMillis() / 1000.0));
    }
}
