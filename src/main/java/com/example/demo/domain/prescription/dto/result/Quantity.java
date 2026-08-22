package com.example.demo.domain.prescription.dto.result;

public record Quantity(
    Double value,
    String unit,
    String rawText
) {
}
