package com.example.demo.domain.test.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.domain.test.service.TestService;
import com.example.demo.global.response.ApiResponse;
import com.example.demo.global.storage.StorageService;

import lombok.RequiredArgsConstructor;

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

    @PostMapping("/file")
    public ApiResponse<String> getFile(@RequestParam MultipartFile file) {
        return ApiResponse.of(storageService.upload(file));
    }
}
