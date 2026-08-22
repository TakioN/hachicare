package com.example.demo.domain.prescription.dto;

import java.time.Instant;

import com.example.demo.domain.prescription.entity.ExtractionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.JsonNode;

/**
 * 작업 조회 응답.
 *
 * <p>상태별로 채워지는 필드가 다르다. null 필드를 직렬화하지 않는 것으로
 * "pending에는 result/failure/completedAt이 없다", "result와 failure는 동시에 없다"는
 * 문서의 불변조건이 응답에서도 그대로 드러난다.
 *
 * <p>result는 저장된 JSON을 그대로 싣는다. 결과 스키마는 7단계에서 타입으로 굳힌다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtractionJobResponse(
    String id,
    ExtractionStatus status,
    Instant createdAt,
    Instant completedAt,
    JsonNode result,
    ExtractionFailureResponse failure
) {
}
