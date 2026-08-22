package com.example.demo.domain.prescription.dto.result;

import java.util.List;

/**
 * 분석 결과. 프런트와의 계약이며 업스트림 응답을 그대로 노출하지 않는다.
 *
 * <p>약물이 하나여도 항상 medications 배열로 돌려준다.
 */
public record PrescriptionExtractionResult(
    ReviewStatus reviewStatus,
    DocumentType documentType,
    List<MedicationExtraction> medications,
    List<ExtractionIssue> issues
) {
}
