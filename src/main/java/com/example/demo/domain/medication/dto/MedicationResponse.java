package com.example.demo.domain.medication.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.example.demo.domain.medication.entity.Medication;
import com.example.demo.domain.medication.entity.MedicationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MedicationResponse(
    String id,
    String sourceExtractionId,
    String drugName,
    Double doseValue,
    String doseUnit,
    Integer frequencyPerDay,
    Integer durationDays,
    String timingInstruction,
    LocalDate startedOn,
    LocalDate endedOn,
    MedicationStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static MedicationResponse from(Medication medication) {
        return new MedicationResponse(
                medication.getPublicId(),
                medication.getSourceExtractionId(),
                medication.getDrugName(),
                medication.getDoseValue(),
                medication.getDoseUnit(),
                medication.getFrequencyPerDay(),
                medication.getDurationDays(),
                medication.getTimingInstruction(),
                medication.getStartedOn(),
                medication.getEndedOn(),
                medication.getStatus(),
                medication.getCreatedAt(),
                medication.getUpdatedAt());
    }
}
