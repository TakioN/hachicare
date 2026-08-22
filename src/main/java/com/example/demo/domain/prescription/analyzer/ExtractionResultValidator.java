package com.example.demo.domain.prescription.analyzer;

import org.springframework.stereotype.Component;

import com.example.demo.domain.prescription.dto.result.ExtractedField;
import com.example.demo.domain.prescription.dto.result.FieldStatus;
import com.example.demo.domain.prescription.dto.result.MedicationExtraction;
import com.example.demo.domain.prescription.dto.result.PrescriptionExtractionResult;
import com.example.demo.domain.prescription.entity.ExtractionFailureCode;

/**
 * 분석 결과가 프런트와의 계약을 지키는지 확인한다.
 *
 * <p>업스트림이 LLM 기반이라 필드가 통째로 빠지거나 값 없이 상태만 오는 응답이 나올 수 있다.
 * 그대로 저장하면 클라이언트가 completed를 받아놓고 렌더링에 실패한다. 저장 전에 막는다.
 */
@Component
public class ExtractionResultValidator {

    public void validate(PrescriptionExtractionResult result) {
        require(result != null, "결과가 비어 있음");
        require(result.reviewStatus() != null, "reviewStatus 누락");
        require(result.documentType() != null, "documentType 누락");
        require(result.medications() != null, "medications 누락");
        require(result.issues() != null, "issues 누락");

        for (MedicationExtraction medication : result.medications()) {
            validateMedication(medication);
        }
    }

    private void validateMedication(MedicationExtraction medication) {
        require(medication != null, "medications에 빈 항목이 있음");
        require(hasText(medication.id()), "약물 id 누락");

        String prefix = "약물 " + medication.id() + "의 ";
        requireField(medication.drugName(), prefix + "drugName");
        requireField(medication.dose(), prefix + "dose");
        requireField(medication.frequencyPerDay(), prefix + "frequencyPerDay");
        requireField(medication.durationDays(), prefix + "durationDays");

        require(medication.timingInstruction() != null, prefix + "timingInstruction 누락");
        requireField(medication.timingInstruction().source(), prefix + "timingInstruction.source");
    }

    private void requireField(ExtractedField<?> field, String name) {
        require(field != null, name + " 누락");
        require(field.status() != null, name + "의 status 누락");
        // 추출했다고 해놓고 값이 없으면 클라이언트가 렌더링할 것이 없다
        require(field.status() != FieldStatus.EXTRACTED || field.value() != null,
                name + "이 extracted인데 value가 없음");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean satisfied, String detail) {
        if (!satisfied) {
            throw new AnalysisFailedException(ExtractionFailureCode.INVALID_AGENT_RESPONSE, detail);
        }
    }
}
