package com.example.demo.domain.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.domain.auth.dto.SignInRequest;
import com.example.demo.domain.auth.dto.SignUpRequest;
import com.example.demo.domain.auth.dto.TokenResponse;
import com.example.demo.domain.auth.entity.User;
import com.example.demo.domain.auth.repository.UserRepository;
import com.example.demo.global.exception.CustomException;
import com.example.demo.global.exception.ErrorCode;
import com.example.demo.global.jwt.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    @Transactional
    public void signUp(SignUpRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        userRepository.save(User.of(request.email(), passwordEncoder.encode(request.password())));
    }

    @Transactional(readOnly = true)
    public TokenResponse signIn(SignInRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            // 존재하지 않는 계정과 같은 응답을 준다. 어느 이메일이 가입돼 있는지 알려주지 않는다.
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        return issueTokens(user.getPublicId());
    }

    /**
     * 재발급. 쓴 refresh 토큰은 즉시 폐기하고 새로 발급한다(회전).
     *
     * <p>회전하지 않으면 유출된 토큰이 만료일까지 계속 유효하다.
     */
    public TokenResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        // 서명·만료가 유효해도 저장소에 없으면 이미 쓰였거나 로그아웃된 토큰이다.
        String subject = tokenProvider.parseSubject(refreshToken);
        String owner = refreshTokenStore.findOwner(refreshToken)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));

        if (!owner.equals(subject)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        refreshTokenStore.remove(refreshToken);
        return issueTokens(owner);
    }

    /** 없는 토큰이어도 조용히 성공한다. 존재 여부를 알려주지 않고, 재시도해도 안전하다. */
    public void signOut(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenStore.remove(refreshToken);
        }
    }

    private TokenResponse issueTokens(String userPublicId) {
        String accessToken = tokenProvider.createAccessToken(userPublicId);
        String refreshToken = tokenProvider.createRefreshToken(userPublicId);

        refreshTokenStore.save(refreshToken, userPublicId, tokenProvider.refreshTokenValidity());

        return new TokenResponse(accessToken, refreshToken);
    }
}
