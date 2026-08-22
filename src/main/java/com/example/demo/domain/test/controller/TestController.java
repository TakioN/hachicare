package com.example.demo.domain.test.controller;

import com.example.demo.domain.test.service.StorageService;
import org.springframework.web.bind.annotation.*;

import com.example.demo.domain.test.dto.SignInRequestDto;
import com.example.demo.domain.test.dto.SignInResponseDto;
import com.example.demo.domain.test.dto.SignUpRequestDto;
import com.example.demo.domain.test.service.TestService;
import com.example.demo.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TestController {
    private final TestService testService;
    private final StorageService storageService;

    @GetMapping("/health")
    public ApiResponse<String> healthCheck() {
        return ApiResponse.of("Connected...");
    }

    @GetMapping("/{id}")
    public ApiResponse<String> getDescription(@PathVariable("id") Long id) {
        String desc = testService.getDesc(id);
        return ApiResponse.of(desc);
    }

    @PostMapping("/signup")
    public ApiResponse<Void> postMethodName(@RequestBody SignUpRequestDto userInfo) {
        testService.signUp(userInfo);
        return ApiResponse.empty();
    }

    @PostMapping("/signin")
    public ApiResponse<SignInResponseDto> signin(@RequestBody SignInRequestDto signinReq) {
        SignInResponseDto data = testService.signIn(signinReq);
        return ApiResponse.of(data);
    }

    @PostMapping("/file")
    public ApiResponse<String> getFile(@RequestParam MultipartFile file) {
        String objectKey = storageService.upload(file);
        return ApiResponse.of(objectKey);
    }

    
}
