package com.example.demo.domain.medication.dto;

import java.time.LocalDate;

public record MedicationFinishRequest(
    LocalDate endedOn
) {
}
