package com.example.demo.domain.prescription.dto.result;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum DocumentType {
    PATIENT_COPY_PRESCRIPTION("patient_copy_prescription"),
    OTHER("other");

    @JsonValue
    private final String value;
}
