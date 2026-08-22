package com.example.demo.domain.test.dto;

public record SignInResponseDto(
    String accessToken,
    String refreshToken
) {
}
