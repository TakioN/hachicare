package com.example.demo.domain.prescription.dto.result;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MedicationExtraction(
    String id,

    ExtractedField<String> drugName,
    ExtractedField<String> printedProductCode,
    ExtractedField<Quantity> strength,
    ExtractedField<String> dosageForm,

    ExtractedField<Quantity> dose,
    ExtractedField<Integer> frequencyPerDay,
    ExtractedField<Integer> durationDays,

    TimingInstruction timingInstruction,

    ExtractedField<String> route,
    ExtractedField<Boolean> asNeeded
) {
}
