package com.example.demo.domain.prescription.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.demo.domain.prescription.dto.result.DocumentType;
import com.example.demo.domain.prescription.dto.result.ExtractedField;
import com.example.demo.domain.prescription.dto.result.FieldStatus;
import com.example.demo.domain.prescription.dto.result.MedicationExtraction;
import com.example.demo.domain.prescription.dto.result.PrescriptionExtractionResult;
import com.example.demo.domain.prescription.dto.result.ReviewStatus;
import com.example.demo.domain.prescription.entity.ExtractionFailureCode;
import com.example.demo.support.TestResults;

class ExtractionResultValidatorTest {

    private final ExtractionResultValidator validator = new ExtractionResultValidator();

    private void assertRejected(PrescriptionExtractionResult result) {
        assertThatThrownBy(() -> validator.validate(result))
                .isInstanceOf(AnalysisFailedException.class)
                .extracting(e -> ((AnalysisFailedException) e).getFailureCode())
                .isEqualTo(ExtractionFailureCode.INVALID_AGENT_RESPONSE);
    }

    private static PrescriptionExtractionResult withMedications(MedicationExtraction... medications) {
        return new PrescriptionExtractionResult(
                ReviewStatus.READY,
                DocumentType.PATIENT_COPY_PRESCRIPTION,
                Arrays.asList(medications),
                List.of());
    }

    private static MedicationExtraction medicationWithDrugName(ExtractedField<String> drugName) {
        MedicationExtraction base = TestResults.medication("med_1");
        return new MedicationExtraction(
                base.id(), drugName, null, null, null,
                base.dose(), base.frequencyPerDay(), base.durationDays(),
                base.timingInstruction(), null, null);
    }

    @Test
    void 계약을_지키는_결과는_통과한다() {
        assertThatCode(() -> validator.validate(TestResults.valid())).doesNotThrowAnyException();
    }

    @Test
    void 약물이_없어도_결과_자체는_유효하다() {
        assertThatCode(() -> validator.validate(withMedications())).doesNotThrowAnyException();
    }

    @Test
    void 최상위_필드가_빠지면_거부한다() {
        assertRejected(null);
        assertRejected(new PrescriptionExtractionResult(
                null, DocumentType.OTHER, List.of(), List.of()));
        assertRejected(new PrescriptionExtractionResult(
                ReviewStatus.READY, null, List.of(), List.of()));
        assertRejected(new PrescriptionExtractionResult(
                ReviewStatus.READY, DocumentType.OTHER, null, List.of()));
        assertRejected(new PrescriptionExtractionResult(
                ReviewStatus.READY, DocumentType.OTHER, List.of(), null));
    }

    @Test
    void 약물_id가_없으면_거부한다() {
        // 프런트가 issue를 약물에 연결할 수 없게 된다
        assertRejected(withMedications(TestResults.medication(null)));
        assertRejected(withMedications(TestResults.medication("  ")));
    }

    @Test
    void 필수_추출_필드가_통째로_없으면_거부한다() {
        assertRejected(withMedications(medicationWithDrugName(null)));
    }

    @Test
    void status가_없는_필드는_거부한다() {
        assertRejected(withMedications(
                medicationWithDrugName(new ExtractedField<>("약", null, null, null))));
    }

    @Test
    void extracted라고_해놓고_값이_없으면_거부한다() {
        // completed를 받은 클라이언트가 렌더링할 것이 없어진다
        assertRejected(withMedications(
                medicationWithDrugName(new ExtractedField<>(null, FieldStatus.EXTRACTED, null, null))));
    }

    @Test
    void 읽지_못한_필드는_값이_없어도_유효하다() {
        assertThatCode(() -> validator.validate(withMedications(
                medicationWithDrugName(new ExtractedField<>(null, FieldStatus.UNREADABLE, null, null)))))
                .doesNotThrowAnyException();
    }

    @Test
    void 실패_사유를_메시지에_남긴다() {
        assertThatThrownBy(() -> validator.validate(withMedications(TestResults.medication(null))))
                .hasMessageContaining("id");
    }

    @Test
    void 스텁_분석기의_결과도_계약을_지킨다() {
        assertThatCode(() -> validator.validate(new StubPrescriptionAnalyzer().analyze("k")))
                .doesNotThrowAnyException();
    }

    @Test
    void 실패코드는_문서에_정의된_값이다() {
        assertThat(ExtractionFailureCode.INVALID_AGENT_RESPONSE.getValue())
                .isEqualTo("invalid_agent_response");
    }
}
