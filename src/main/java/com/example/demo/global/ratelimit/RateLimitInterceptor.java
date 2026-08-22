package com.example.demo.global.ratelimit;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.demo.global.exception.CustomException;
import com.example.demo.global.exception.ErrorCode;
import com.example.demo.global.ratelimit.RateLimitProperties.Bucket;
import com.example.demo.global.session.AnonymousSessionManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 분석 요청 한도.
 *
 * <p>세션과 IP 두 기준으로 센다. 익명 세션 쿠키는 사용자가 버리고 새로 받으면 그만이라
 * 세션만으로는 남용을 막지 못한다. 반대로 IP만 쓰면 같은 공유망(행사장 와이파이 등)에 있는
 * 사용자들이 서로의 한도를 잡아먹는다. 그래서 세션은 좁게, IP는 넉넉하게 잡는다.
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "rate:extraction:";

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final AnonymousSessionManager sessionManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.enabled()) {
            return true;
        }

        // 첫 요청은 아직 쿠키가 없다. 그 경우 IP 기준만 적용된다.
        sessionManager.find(request).ifPresent(sessionKey ->
                check(KEY_PREFIX + "session:" + sessionKey, properties.session(), response));

        check(KEY_PREFIX + "ip:" + request.getRemoteAddr(), properties.ip(), response);

        return true;
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
