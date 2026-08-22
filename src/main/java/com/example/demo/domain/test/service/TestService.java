package com.example.demo.domain.test.service;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.demo.domain.test.dto.SignInRequestDto;
import com.example.demo.domain.test.dto.SignInResponseDto;
import com.example.demo.domain.test.dto.SignUpRequestDto;
import com.example.demo.domain.test.entity.User;
import com.example.demo.domain.test.repository.TestRepository;
import com.example.demo.global.exception.CustomException;
import com.example.demo.global.exception.ErrorCode;
import com.example.demo.global.jwt.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TestService {
    private final TestRepository testRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenService tokenService;

    public String getDesc(Long id) {
        User t  = testRepository.findById(id).orElseThrow(() -> new CustomException(ErrorCode.TESTCODE));
        return t.getDescription();
    }

    public void signUp(SignUpRequestDto info) {
        if(testRepository.existsByUserId(info.userId())) throw new IllegalStateException("이미 가입한 회원입니다");

        String encodedPw = passwordEncoder.encode(info.password());
        User newUser = User.builder()
        .email(info.email())
        .userId(info.userId())
        .password(encodedPw)
        .build();
        testRepository.save(newUser);
    }

    public SignInResponseDto signIn(SignInRequestDto signinReq) {
        // 유저 검증
        User user = testRepository.findByEmail(signinReq.email()).orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 유저 입니다"));

        // 비밀번호 검증
        if(!passwordEncoder.matches(signinReq.password(), user.getPassword())){
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다");
        }

        String acT = jwtTokenProvider.createAccessToken(signinReq.email());
        String refT = jwtTokenProvider.createRefreshToken(signinReq.email());

        // Redis에 저장
        tokenService.saveRefreshToken(refT, signinReq.email());


        return new SignInResponseDto(acT, refT);
    }
}