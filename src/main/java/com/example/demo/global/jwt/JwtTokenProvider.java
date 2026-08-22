package com.example.demo.global.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {
    private final SecretKey key;
    private final long accessTokenValidity; // acT 유효기간
    private final long refreshTokenValidity; // reT 유효기간

    public JwtTokenProvider(
        @Value("${jwt.secret}") String secretKey, 
        @Value("${jwt.access-exp}") long accessTokenValidity,
        @Value("${jwt.refresh-exp}") long refreshTokenValidity
    ) {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = accessTokenValidity;
        this.refreshTokenValidity = refreshTokenValidity;
    }

    // Access Token 발급
    public String createAccessToken(String email) {
        Date now = new Date();
        Date expiDate = new Date(now.getTime() + accessTokenValidity);

        return Jwts.builder()
        .subject(email)
        .issuedAt(now)
        .expiration(expiDate)
        .signWith(key)
        .compact();
    }

    // Refresh Token 발급
    public String createRefreshToken(String email) {
        Date now = new Date();
        Date exDate = new Date(now.getTime() + refreshTokenValidity);

        return Jwts.builder()
        .subject(email)
        .issuedAt(now)
        .expiration(exDate)
        .signWith(key)
        .compact();
    }
}
