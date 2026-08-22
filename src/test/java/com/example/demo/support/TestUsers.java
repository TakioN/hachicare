package com.example.demo.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import com.example.demo.domain.auth.entity.User;
import com.example.demo.domain.auth.repository.UserRepository;
import com.example.demo.global.jwt.JwtTokenProvider;

/**
 * 통합 테스트용 사용자와 토큰.
 *
 * <p>목 인증 대신 실제 토큰을 발급해 헤더로 보낸다. 그래야 JwtAuthenticationFilter까지
 * 함께 검증된다.
 */
@Component
public class TestUsers {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    public User create(String email) {
        return userRepository.save(User.of(email, "{bcrypt-not-used-here}"));
    }

    public String bearerFor(String userPublicId) {
        return "Bearer " + tokenProvider.createAccessToken(userPublicId);
    }

    /** 사용자를 만들고 그 사용자의 Authorization 헤더 값을 돌려준다. */
    public String createAndAuthorize(String email) {
        return bearerFor(create(email).getPublicId());
    }

    public static String header() {
        return HttpHeaders.AUTHORIZATION;
    }
}
