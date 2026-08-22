package com.example.demo.domain.prescription.dto;

import com.example.demo.domain.prescription.entity.ExtractionFailureCode;
import com.example.demo.domain.prescription.entity.PrescriptionExtraction;

public record ExtractionFailureResponse(
    ExtractionFailureCode code,
    String message
) {
    public static ExtractionFailureResponse from(PrescriptionExtraction extraction) {
        if (extraction.getFailureCode() == null) {
            return null;
        }
        return new ExtractionFailureResponse(
                extraction.getFailureCode(), extraction.getFailureMessage());
    }
}
