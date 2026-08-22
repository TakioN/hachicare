package com.example.demo.domain.auth.service;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 유효한 refresh 토큰 목록. 로그아웃과 회전을 이 저장소가 성립시킨다.
 *
 * <p>서명과 만료만으로는 로그아웃이 성립하지 않는다. 발급한 토큰을 여기서 지워야
 * 더 이상 재발급에 쓸 수 없다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;

    public void save(String refreshToken, String userPublicId, Duration validity) {
        redisTemplate.opsForValue().set(key(refreshToken), userPublicId, validity);
    }

    public Optional<String> findOwner(String refreshToken) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(refreshToken)));
    }

    /** 회전과 로그아웃 모두 여기를 지난다. 없는 토큰을 지워도 조용히 넘어간다. */
    public void remove(String refreshToken) {
        redisTemplate.delete(key(refreshToken));
    }

    private static String key(String refreshToken) {
        return KEY_PREFIX + refreshToken;
    }
}
