package com.example.demo.global.exception;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ErrorCode {
    TESTCODE(HttpStatus.BAD_REQUEST, "Test Error");

    private final HttpStatus status;
    private final String message;
}
