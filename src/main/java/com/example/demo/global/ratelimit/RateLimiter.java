package com.example.demo.global.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 고정 윈도우 카운터.
 *
 * <p>INCR과 만료 설정이 따로 나가면 그 사이에 죽었을 때 TTL 없는 키가 남아 영구 차단이 된다.
 * 한 스크립트로 묶어 원자적으로 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private static final String INCREMENT_AND_READ_TTL = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return {current, redis.call('PTTL', KEYS[1])}
            """;

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT =
            RedisScript.of(INCREMENT_AND_READ_TTL, List.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 요청 하나를 센다.
     *
     * @return 허용되면 비어 있고, 한도를 넘었으면 윈도우가 끝날 때까지 남은 시간
     */
    @SuppressWarnings("unchecked")
    public Optional<Duration> exceeded(String key, int limit, Duration window) {
        List<Long> result;
        try {
            result = redisTemplate.execute(SCRIPT, List.of(key), String.valueOf(window.toMillis()));
        } catch (RuntimeException e) {
            // Redis가 죽었다고 서비스 전체를 막지는 않는다. 한도 없이 통과시키고 경보만 남긴다.
            log.error("레이트 리밋 판정 실패, 통과시킨다: key={}", key, e);
            return Optional.empty();
        }

        if (result == null || result.size() < 2) {
            log.error("레이트 리밋 스크립트가 예상 밖의 값을 반환했다: key={} result={}", key, result);
            return Optional.empty();
        }

        long current = result.get(0);
        if (current <= limit) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofMillis(Math.max(result.get(1), 0)));
    }
}
