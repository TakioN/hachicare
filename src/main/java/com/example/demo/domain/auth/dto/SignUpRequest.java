package com.example.demo.domain.auth.dto;

public record SignUpRequest(
    String email,
    String password
) {
}
