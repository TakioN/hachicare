package com.example.demo.domain.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.domain.auth.dto.RefreshRequest;
import com.example.demo.domain.auth.dto.SignInRequest;
import com.example.demo.domain.auth.dto.SignInResponse;
import com.example.demo.domain.auth.dto.SignUpRequest;
import com.example.demo.domain.auth.dto.TokenResponse;
import com.example.demo.domain.auth.service.AuthService;
import com.example.demo.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> signUp(@RequestBody SignUpRequest request) {
        authService.signUp(request);
        return ApiResponse.empty();
    }

    @PostMapping("/signin")
    public ApiResponse<SignInResponse> signIn(@RequestBody SignInRequest request) {
        return ApiResponse.of(authService.signIn(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@RequestBody RefreshRequest request) {
        return ApiResponse.of(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/signout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void signOut(@RequestBody RefreshRequest request) {
        authService.signOut(request.refreshToken());
    }
}
