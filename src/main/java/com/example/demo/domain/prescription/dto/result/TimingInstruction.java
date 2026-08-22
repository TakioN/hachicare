package com.example.demo.domain.prescription.dto.result;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 복약 지시. 원문(source)은 그대로 두고 번역은 곁들이기만 한다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimingInstruction(
    ExtractedField<String> source,
    String english
) {
}
