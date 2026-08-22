package com.example.demo.domain.medication.entity;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MedicationStatus {
    /** 복용 중. */
    ACTIVE("active"),
    /** 복용 종료. 기록은 남긴다. */
    FINISHED("finished");

    @JsonValue
    private final String value;
}
