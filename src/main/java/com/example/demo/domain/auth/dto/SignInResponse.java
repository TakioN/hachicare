package com.example.demo.domain.auth.dto;

/**
 * 로그인 응답. 토큰에 더해 누구로 로그인됐는지 알려준다.
 *
 * <p>재발급(TokenResponse)에는 넣지 않는다. 재발급은 Redis와 토큰만으로 처리되는데
 * 이메일을 실으려면 사용자 조회가 한 번 더 필요해진다.
 */
public record SignInResponse(
    String email,
    String accessToken,
    String refreshToken
) {
    public static SignInResponse of(String email, TokenResponse tokens) {
        return new SignInResponse(email, tokens.accessToken(), tokens.refreshToken());
    }
}
