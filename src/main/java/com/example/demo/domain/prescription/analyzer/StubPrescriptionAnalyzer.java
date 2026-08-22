package com.example.demo.domain.prescription.analyzer;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.demo.domain.prescription.dto.result.DocumentType;
import com.example.demo.domain.prescription.dto.result.ExtractedField;
import com.example.demo.domain.prescription.dto.result.ExtractionConfidence;
import com.example.demo.domain.prescription.dto.result.ExtractionIssue;
import com.example.demo.domain.prescription.dto.result.ExtractionIssueCode;
import com.example.demo.domain.prescription.dto.result.FieldStatus;
import com.example.demo.domain.prescription.dto.result.MedicationExtraction;
import com.example.demo.domain.prescription.dto.result.PrescriptionExtractionResult;
import com.example.demo.domain.prescription.dto.result.Quantity;
import com.example.demo.domain.prescription.dto.result.ReviewStatus;
import com.example.demo.domain.prescription.dto.result.TimingInstruction;

import lombok.extern.slf4j.Slf4j;

/**
 * Upstage 연동 전까지 쓰는 임시 구현.
 *
 * <p>프런트가 폴링과 결과 렌더링을 먼저 붙여볼 수 있도록 계약대로 된 결과를 돌려준다.
 * {@code app.analyzer=upstage}로 바꾸면 실제 구현으로 교체된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.analyzer", havingValue = "stub", matchIfMissing = true)
public class StubPrescriptionAnalyzer implements PrescriptionAnalyzer {

    @Override
    public PrescriptionExtractionResult analyze(String imageKey) {
        log.warn("스텁 분석기가 동작 중이다. 실제 분석이 아니다: imageKey={}", imageKey);

        return new PrescriptionExtractionResult(
                ReviewStatus.NEEDS_REVIEW,
                DocumentType.PATIENT_COPY_PRESCRIPTION,
                List.of(sampleMedication()),
                List.of(new ExtractionIssue(
                        ExtractionIssueCode.LOW_CONFIDENCE,
                        "med_1",
                        "durationDays",
                        "투약 일수를 확인해 주세요.",
                        null)));
    }

    private static MedicationExtraction sampleMedication() {
        return new MedicationExtraction(
                "med_1",
                extracted("아모잘탄정 5/50mg", ExtractionConfidence.HIGH),
                null,
                null,
                null,
                extracted(new Quantity(1.0, "정", "1정"), ExtractionConfidence.HIGH),
                extracted(1, ExtractionConfidence.HIGH),
                extracted(30, ExtractionConfidence.LOW),
                new TimingInstruction(
                        extracted("아침 식후 30분", ExtractionConfidence.HIGH),
                        "30 minutes after breakfast"),
                null,
                null);
    }

    private static <T> ExtractedField<T> extracted(T value, ExtractionConfidence confidence) {
        return new ExtractedField<>(value, FieldStatus.EXTRACTED, confidence, null);
    }
}
