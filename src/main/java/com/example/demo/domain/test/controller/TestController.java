package com.example.demo.domain.test.controller;

import org.springframework.web.bind.annotation.RestController;

import com.example.demo.domain.test.dto.SignInRequestDto;
import com.example.demo.domain.test.dto.SignInResponseDto;
import com.example.demo.domain.test.dto.SignUpRequestDto;
import com.example.demo.domain.test.service.TestService;
import com.example.demo.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TestController {
    private final TestService testService;

    @GetMapping("/{id}")
    public ApiResponse<String> getDescription(@PathVariable("id") Long id) {
        String desc = testService.getDesc(id);
        return ApiResponse.success("조회성공", desc);
    }

    @PostMapping("/signup")
    public ApiResponse<Void> postMethodName(@RequestBody SignUpRequestDto userInfo) {
        testService.signUp(userInfo);
        return ApiResponse.success("회원가입 성공");
    }
    
    @PostMapping("/signin")
    public ApiResponse<SignInResponseDto> postMethodName(@RequestBody SignInRequestDto signinReq) {
        SignInResponseDto data = testService.signIn(signinReq);
        return ApiResponse.success("로그인 성공", data);
    }
    
}
