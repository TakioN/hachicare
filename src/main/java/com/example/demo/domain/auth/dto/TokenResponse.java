package com.example.demo.domain.auth.dto;

public record TokenResponse(
    String accessToken,
    String refreshToken
) {
}
