package com.example.demo.domain.prescription.dto.result;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ExtractionConfidence {
    HIGH("high"),
    LOW("low");

    @JsonValue
    private final String value;
}
