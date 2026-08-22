package com.example.demo.domain.prescription.dto.result;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum FieldStatus {
    EXTRACTED("extracted"),
    NOT_PRESENT("not_present"),
    UNREADABLE("unreadable");

    @JsonValue
    private final String value;
}
