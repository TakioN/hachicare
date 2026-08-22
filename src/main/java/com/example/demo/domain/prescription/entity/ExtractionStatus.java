package com.example.demo.domain.prescription.entity;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ExtractionStatus {
    PENDING("pending"),
    COMPLETED("completed"),
    FAILED("failed");

    @JsonValue
    private final String value;
}
