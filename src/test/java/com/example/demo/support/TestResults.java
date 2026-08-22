package com.example.demo.support;

import java.util.List;

import com.example.demo.domain.prescription.dto.result.DocumentType;
import com.example.demo.domain.prescription.dto.result.ExtractedField;
import com.example.demo.domain.prescription.dto.result.ExtractionConfidence;
import com.example.demo.domain.prescription.dto.result.FieldStatus;
import com.example.demo.domain.prescription.dto.result.MedicationExtraction;
import com.example.demo.domain.prescription.dto.result.PrescriptionExtractionResult;
import com.example.demo.domain.prescription.dto.result.Quantity;
import com.example.demo.domain.prescription.dto.result.ReviewStatus;
import com.example.demo.domain.prescription.dto.result.TimingInstruction;

public final class TestResults {

    private TestResults() {
    }

    public static <T> ExtractedField<T> extracted(T value) {
        return new ExtractedField<>(value, FieldStatus.EXTRACTED, ExtractionConfidence.HIGH, null);
    }

    public static MedicationExtraction medication(String id) {
        return new MedicationExtraction(
                id,
                extracted("아모잘탄정 5/50mg"),
                null, null, null,
                extracted(new Quantity(1.0, "정", "1정")),
                extracted(1),
                extracted(30),
                new TimingInstruction(extracted("아침 식후 30분"), "30 minutes after breakfast"),
                null, null);
    }

    /** 검증을 통과하는 최소 결과. */
    public static PrescriptionExtractionResult valid() {
        return new PrescriptionExtractionResult(
                ReviewStatus.READY,
                DocumentType.PATIENT_COPY_PRESCRIPTION,
                List.of(medication("med_1")),
                List.of());
    }
}
