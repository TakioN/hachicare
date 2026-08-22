package com.example.demo.domain.test.dto;


public record SignUpRequestDto(
    String userId,
    String password,
    String email
) {
}
