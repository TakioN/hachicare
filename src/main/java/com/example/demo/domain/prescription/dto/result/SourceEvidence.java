package com.example.demo.domain.prescription.dto.result;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 추출값이 원문의 어디서 나왔는지. 사용자가 직접 대조할 수 있어야 하므로 원문과 좌표를 함께 남긴다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceEvidence(
    Integer page,
    List<SourceCoordinate> coordinates,
    String rawText
) {
}
