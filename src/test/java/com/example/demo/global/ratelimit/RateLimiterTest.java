package com.example.demo.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private RateLimiter rateLimiter;

    @SuppressWarnings("unchecked")
    private void givenScriptReturns(Object value) {
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .willReturn(value);
    }

    @Test
    void 한도_이내면_통과시킨다() {
        givenScriptReturns(List.of(3L, 60_000L));

        assertThat(rateLimiter.exceeded("k", 5, WINDOW)).isEmpty();
    }

    @Test
    void 한도와_같으면_아직_통과다() {
        givenScriptReturns(List.of(5L, 60_000L));

        assertThat(rateLimiter.exceeded("k", 5, WINDOW)).isEmpty();
    }

    @Test
    void 한도를_넘으면_남은_시간을_돌려준다() {
        givenScriptReturns(List.of(6L, 60_000L));

        Optional<Duration> retryAfter = rateLimiter.exceeded("k", 5, WINDOW);

        assertThat(retryAfter).contains(Duration.ofSeconds(60));
    }

    @Test
    void TTL이_음수여도_음수_시간을_돌려주지_않는다() {
        // PTTL은 키가 막 사라지면 -1/-2를 준다
        givenScriptReturns(List.of(6L, -1L));

        assertThat(rateLimiter.exceeded("k", 5, WINDOW)).contains(Duration.ZERO);
    }

    @Test
    void Redis가_죽으면_막지_않고_통과시킨다() {
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .willThrow(new QueryTimeoutException("redis down"));

        assertThat(rateLimiter.exceeded("k", 1, WINDOW)).isEmpty();
    }

    @Test
    void 응답이_예상_밖이어도_통과시킨다() {
        givenScriptReturns(null);
        assertThat(rateLimiter.exceeded("k", 1, WINDOW)).isEmpty();

        givenScriptReturns(List.of(1L));
        assertThat(rateLimiter.exceeded("k", 1, WINDOW)).isEmpty();
    }
}
