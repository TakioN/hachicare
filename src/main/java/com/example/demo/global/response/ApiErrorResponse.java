package com.example.demo.global.response;

import com.example.demo.global.exception.ErrorCode;

public record ApiErrorResponse(
    Error error
) {
    public record Error(
        String code,
        String message
    ) {
    }

    public static ApiErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getMessage());
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String message) {
        return new ApiErrorResponse(new Error(errorCode.getCode(), message));
    }
}
