package com.example.demo.global.response;

public record ApiResponse<T>(
    T data
) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }

    public static ApiResponse<Void> empty() {
        return new ApiResponse<>(null);
    }
}
