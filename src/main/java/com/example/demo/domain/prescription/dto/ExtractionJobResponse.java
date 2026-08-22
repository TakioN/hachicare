package com.example.demo.domain.prescription.dto;

import java.time.Instant;

import com.example.demo.domain.prescription.entity.ExtractionStatus;
import com.example.demo.domain.prescription.entity.PrescriptionExtraction;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 작업 조회 응답. pending 단계에서는 completedAt이 없어야 하므로 null 필드는 직렬화하지 않는다.
 * result/failure는 상태 조회(5단계)에서 덧붙인다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtractionJobResponse(
    String id,
    ExtractionStatus status,
    Instant createdAt,
    Instant completedAt
) {
    public static ExtractionJobResponse from(PrescriptionExtraction extraction) {
        return new ExtractionJobResponse(
                extraction.getPublicId(),
                extraction.getStatus(),
                extraction.getCreatedAt(),
                extraction.getCompletedAt());
    }
}
