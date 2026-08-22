package com.example.demo.global.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.demo.global.exception.CustomException;
import com.example.demo.global.exception.ErrorCode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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

    /** subject는 사용자 공개 식별자다. 이메일이 바뀌어도 토큰이 가리키는 대상은 그대로여야 한다. */
    public String createAccessToken(String userPublicId) {
        return create(userPublicId, accessTokenValidity);
    }

    public String createRefreshToken(String userPublicId) {
        return create(userPublicId, refreshTokenValidity);
    }

    private String create(String userPublicId, long validity) {
        Date now = new Date();
        return Jwts.builder()
                // jti가 없으면 같은 초에 같은 사용자로 발급한 토큰이 글자까지 똑같아진다.
                // 그러면 refresh 회전이 헛돌고 일회용 성질이 깨진다.
                .id(UUID.randomUUID().toString())
                .subject(userPublicId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validity))
                .signWith(key)
                .compact();
    }

    /**
     * 서명과 만료를 검증하고 subject를 돌려준다.
     *
     * <p>만료와 위조를 구분해서 던진다. 프런트가 "재발급하면 되는 상황"과
     * "다시 로그인해야 하는 상황"을 알아야 한다.
     */
    public String parseSubject(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new CustomException(ErrorCode.INVALID_TOKEN);
            }
            return subject;

        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    public Duration refreshTokenValidity() {
        return Duration.ofMillis(refreshTokenValidity);
    }
}
