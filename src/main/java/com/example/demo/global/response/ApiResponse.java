package com.example.demo.global.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(String msg, T data) {
        return new ApiResponse<>(true, msg, data);
    }

    public static ApiResponse<Void> success(String msg) {
        return new ApiResponse<>(true, msg, null);
    }

    public static ApiResponse<Void> fail(String msg) {
        return new ApiResponse<Void>(false, msg, null);
    }
}
