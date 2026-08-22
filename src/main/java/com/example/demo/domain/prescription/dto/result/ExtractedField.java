package com.example.demo.domain.prescription.dto.result;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 추출된 값 하나.
 *
 * <p>값이 없는 이유를 status로 구분한다. 처방전에 애초에 없었던 것과 읽지 못한 것은 다르다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtractedField<T>(
    T value,
    FieldStatus status,
    ExtractionConfidence confidence,
    SourceEvidence evidence
) {
    public boolean isExtracted() {
        return status == FieldStatus.EXTRACTED && value != null;
    }
}
