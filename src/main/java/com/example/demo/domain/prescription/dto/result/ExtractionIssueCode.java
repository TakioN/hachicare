package com.example.demo.domain.prescription.dto.result;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ExtractionIssueCode {
    LOW_CONFIDENCE("low_confidence"),
    MISSING_REQUIRED_FIELD("missing_required_field"),
    UNREADABLE_DOCUMENT("unreadable_document"),
    UNSUPPORTED_DOCUMENT("unsupported_document"),
    INVALID_AGENT_OUTPUT("invalid_agent_output");

    @JsonValue
    private final String value;
}
