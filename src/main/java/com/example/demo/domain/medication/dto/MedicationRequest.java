package com.example.demo.domain.medication.dto;

import java.time.LocalDate;

/** 등록·수정 공통 입력. 약품명 외에는 모두 선택이다. */
public record MedicationRequest(
    String drugName,
    Double doseValue,
    String doseUnit,
    Integer frequencyPerDay,
    Integer durationDays,
    String timingInstruction,
    LocalDate startedOn,
    LocalDate endedOn
) {
}
