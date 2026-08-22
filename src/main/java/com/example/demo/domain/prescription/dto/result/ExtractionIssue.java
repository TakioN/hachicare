package com.example.demo.domain.prescription.dto.result;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtractionIssue(
    ExtractionIssueCode code,
    String medicationId,
    String field,
    String message,
    SourceEvidence evidence
) {
}
