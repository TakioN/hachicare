package com.example.demo.domain.test.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenService {
    private final StringRedisTemplate stringRedisTemplate;

    public void saveRefreshToken(String token, String email) {
        stringRedisTemplate.opsForValue().set("refresToken:" + token, email, Duration.ofDays(14));
    }
}
