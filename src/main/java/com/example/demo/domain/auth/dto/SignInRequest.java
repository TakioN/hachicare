package com.example.demo.domain.auth.dto;

public record SignInRequest(
    String email,
    String password
) {
}
