package com.example.demo.global.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
    boolean enabled,
    Bucket session,
    Bucket ip
) {
    public record Bucket(
        int limit,
        Duration window
    ) {
    }
}
