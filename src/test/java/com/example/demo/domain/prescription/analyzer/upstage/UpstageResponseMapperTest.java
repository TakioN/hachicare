package com.example.demo.domain.prescription.analyzer.upstage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.example.demo.domain.prescription.analyzer.AnalysisFailedException;
import com.example.demo.domain.prescription.dto.result.DocumentType;
import com.example.demo.domain.prescription.dto.result.ExtractionConfidence;
import com.example.demo.domain.prescription.dto.result.ExtractionIssueCode;
import com.example.demo.domain.prescription.dto.result.FieldStatus;
import com.example.demo.domain.prescription.dto.result.MedicationExtraction;
import com.example.demo.domain.prescription.dto.result.PrescriptionExtractionResult;
import com.example.demo.domain.prescription.dto.result.ReviewStatus;
import com.example.demo.domain.prescription.entity.ExtractionFailureCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class UpstageResponseMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final JsonNode LIVE_RESPONSES = loadLiveResponses();

    private final UpstageResponseMapper mapper = new UpstageResponseMapper();

    private static JsonNode loadLiveResponses() {
        InputStream resource = Objects.requireNonNull(
                UpstageResponseMapperTest.class.getResourceAsStream("/upstage/live-responses.json"));
        return JSON.readTree(resource);
    }

    private static JsonNode output(String caseName) {
        return LIVE_RESPONSES.get(caseName).get("output");
    }

    private static ObjectNode content(JsonNode output, String model) {
        for (JsonNode step : output) {
            if (model.equals(step.get("model").stringValue())) {
                return (ObjectNode) step.get("content").get(0);
            }
        }
        throw new IllegalArgumentException("Missing fixture step: " + model);
    }

    @Test
    void mapsLiveGreenResponseButRequiresReviewForLowConfidenceCriticalFields() {
        PrescriptionExtractionResult result = mapper.toResult(output("clear"));

        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.NEEDS_REVIEW);
        assertThat(result.documentType()).isEqualTo(DocumentType.PATIENT_COPY_PRESCRIPTION);
        assertThat(result.medications()).hasSize(2);

        MedicationExtraction first = result.medications().get(0);
        assertThat(first.id()).isEqualTo("med_1");
        assertThat(first.drugName().value()).isEqualTo("테스트정");
        assertThat(first.drugName().status()).isEqualTo(FieldStatus.EXTRACTED);
        assertThat(first.drugName().confidence()).isEqualTo(ExtractionConfidence.LOW);
        assertThat(first.drugName().evidence().page()).isEqualTo(1);
        assertThat(first.drugName().evidence().coordinates()).hasSize(4);
        assertThat(first.drugName().evidence().rawText()).isEqualTo("테스트정");

        assertThat(first.printedProductCode().value()).isNull();
        assertThat(first.printedProductCode().status()).isEqualTo(FieldStatus.NOT_PRESENT);
        assertThat(first.strength().value().value()).isEqualTo(10.0);
        assertThat(first.strength().value().unit()).isEqualTo("mg");
        assertThat(first.dose().value().value()).isEqualTo(1.0);
        assertThat(first.dose().value().unit()).isEqualTo("정");
        assertThat(first.dose().evidence().coordinates()).isEmpty();
        assertThat(first.frequencyPerDay().value()).isEqualTo(3);
        assertThat(first.durationDays().value()).isEqualTo(3);
        assertThat(first.timingInstruction().source().value()).isEqualTo("매 식후 30분");
        assertThat(first.timingInstruction().english()).isEqualTo("30 minutes after each meal");
        assertThat(first.route().status()).isEqualTo(FieldStatus.NOT_PRESENT);
        assertThat(first.asNeeded().status()).isEqualTo(FieldStatus.NOT_PRESENT);

        assertThat(result.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo(ExtractionIssueCode.LOW_CONFIDENCE);
                    assertThat(issue.medicationId()).isEqualTo("med_1");
                    assertThat(issue.field()).isEqualTo("drugName");
                });
    }

    @Test
    void mapsGreenResponseToReadyWhenAllCriticalFieldsAreHighConfidence() {
        JsonNode changed = output("clear").deepCopy();
        ObjectNode extract = content(changed, "Information Extract - prescription_medications");
        ObjectNode metadata = (ObjectNode) JSON.readTree(extract.get("additional_values").stringValue());
        for (JsonNode medication : metadata.get("medications")) {
            ((ObjectNode) medication.get("printed_drug_name")).put("confidence", "high");
            ((ObjectNode) medication.get("printed_drug_name")).put("confidence_score", 0.99);
            ((ObjectNode) medication.get("dose_value")).put("confidence", "high");
            ((ObjectNode) medication.get("dose_unit")).put("confidence", "high");
            ((ObjectNode) medication.get("frequency_per_day")).put("confidence", "high");
            ((ObjectNode) medication.get("frequency_per_day")).put("confidence_score", 0.99);
        }
        extract.put("additional_values", JSON.writeValueAsString(metadata));

        assertThat(mapper.toResult(changed).reviewStatus()).isEqualTo(ReviewStatus.READY);
    }

    @Test
    void mapsLiveYellowResponseToNeedsReviewAndNormalizesZeroDuration() {
        PrescriptionExtractionResult result = mapper.toResult(output("review"));

        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.NEEDS_REVIEW);
        assertThat(result.medications().get(0).durationDays().value()).isNull();
        assertThat(result.medications().get(0).durationDays().status())
                .isEqualTo(FieldStatus.NOT_PRESENT);
        assertThat(result.medications().get(0).timingInstruction().english())
                .isEqualTo("30 minutes after each meal");
    }

    @Test
    void mapsLiveRedResponseToNeedsRetakeAndUnreadableRequiredFields() {
        PrescriptionExtractionResult result = mapper.toResult(output("retake"));

        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.NEEDS_RETAKE);
        MedicationExtraction first = result.medications().get(0);
        assertThat(first.dose().value()).isNull();
        assertThat(first.dose().status()).isEqualTo(FieldStatus.UNREADABLE);
        assertThat(first.frequencyPerDay().value()).isNull();
        assertThat(first.frequencyPerDay().status()).isEqualTo(FieldStatus.UNREADABLE);
        assertThat(first.timingInstruction().english()).isEqualTo("30 minutes after each meal");
    }

    @Test
    void mapsLiveOtherDocumentWithoutRequiringLaterWorkflowSteps() {
        PrescriptionExtractionResult result = mapper.toResult(output("unsupported"));

        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.UNSUPPORTED_DOCUMENT);
        assertThat(result.documentType()).isEqualTo(DocumentType.OTHER);
        assertThat(result.medications()).isEmpty();
        assertThat(result.issues()).singleElement()
                .extracting(issue -> issue.code())
                .isEqualTo(ExtractionIssueCode.UNSUPPORTED_DOCUMENT);
    }

    @Test
    void rejectsAnythingOtherThanWorkflowStepArray() {
        assertRejected(null);
        assertRejected(JSON.nullNode());
        assertRejected(JSON.readTree("{\"reviewStatus\":\"ready\"}"));
        assertRejected(JSON.readTree("[]"));
    }

    @Test
    void rejectsContradictoryClassifyValueAndMetadata() {
        JsonNode changed = output("clear").deepCopy();
        ObjectNode classify = content(changed, "step_2_classify");
        ObjectNode metadata = (ObjectNode) JSON.readTree(classify.get("additional_values").stringValue());
        ((ObjectNode) metadata.get("document_type")).put("_value", "other");
        classify.put("additional_values", JSON.writeValueAsString(metadata));

        assertRejected(changed);
    }

    @Test
    void rejectsExtractedValueThatDoesNotMatchEvidenceValue() {
        JsonNode changed = output("clear").deepCopy();
        ObjectNode extract = content(changed, "Information Extract - prescription_medications");
        ObjectNode values = (ObjectNode) JSON.readTree(extract.get("text").stringValue());
        ((ObjectNode) values.get("medications").get(0)).put("printed_drug_name", "변조된약");
        extract.put("text", JSON.writeValueAsString(values));

        assertRejected(changed);
    }

    @Test
    void rejectsNumericValueThatDoesNotMatchEvidenceValue() {
        JsonNode changed = output("clear").deepCopy();
        ObjectNode extract = content(changed, "Information Extract - prescription_medications");
        ObjectNode values = (ObjectNode) JSON.readTree(extract.get("text").stringValue());
        ((ObjectNode) values.get("medications").get(0)).put("dose_value", 2);
        extract.put("text", JSON.writeValueAsString(values));

        assertRejected(changed);
    }

    @Test
    void rejectsContradictoryValidateTextAndVerdict() {
        JsonNode changed = output("clear").deepCopy();
        ObjectNode validate = content(changed, "Validate - Validate medication plan");
        validate.put("text", JSON.writeValueAsString("red"));

        assertRejected(changed);
    }

    @Test
    void rejectsNonCompletedWorkflowStep() {
        JsonNode changed = output("clear").deepCopy();
        for (JsonNode step : changed) {
            if ("Information Extract - prescription_medications"
                    .equals(step.get("model").stringValue())) {
                ((ObjectNode) step).put("status", "failed");
            }
        }

        assertRejected(changed);
    }

    @Test
    void discardsEnglishTranslationWhenInstructOutputIsNotStrictJson() {
        JsonNode changed = output("clear").deepCopy();
        ObjectNode instruct = content(changed, "Instruct - Translate timing");
        instruct.put("text", JSON.writeValueAsString("Take another dose if symptoms continue."));

        PrescriptionExtractionResult result = mapper.toResult(changed);

        assertThat(result.medications().get(0).timingInstruction().source().value())
                .isEqualTo("매 식후 30분");
        assertThat(result.medications().get(0).timingInstruction().english()).isNull();
    }

    @Test
    void rejectsEvidenceCoordinatesOutsideNormalizedRange() {
        JsonNode changed = output("clear").deepCopy();
        ObjectNode extract = content(changed, "Information Extract - prescription_medications");
        ObjectNode metadata = (ObjectNode) JSON.readTree(extract.get("additional_values").stringValue());
        ObjectNode point = (ObjectNode) metadata.get("medications").get(0)
                .get("printed_drug_name").get("word_coordinates").get(0).get(0);
        point.put("x", 1.2);
        extract.put("additional_values", JSON.writeValueAsString(metadata));

        assertRejected(changed);
    }

    private void assertRejected(JsonNode output) {
        assertThatThrownBy(() -> mapper.toResult(output))
                .isInstanceOf(AnalysisFailedException.class)
                .extracting(error -> ((AnalysisFailedException) error).getFailureCode())
                .isEqualTo(ExtractionFailureCode.INVALID_AGENT_RESPONSE);
    }
}
