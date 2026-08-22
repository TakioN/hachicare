package com.example.demo.domain.prescription.analyzer.upstage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.demo.domain.prescription.analyzer.AnalysisFailedException;
import com.example.demo.domain.prescription.dto.result.DocumentType;
import com.example.demo.domain.prescription.dto.result.FieldStatus;
import com.example.demo.domain.prescription.dto.result.PrescriptionExtractionResult;
import com.example.demo.domain.prescription.dto.result.ReviewStatus;
import com.example.demo.domain.prescription.entity.ExtractionFailureCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.StringNode;

class UpstageResponseMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final UpstageResponseMapper mapper = new UpstageResponseMapper();

    private static JsonNode json(String raw) {
        return JSON.readTree(raw);
    }

    private static final String CAMEL_CASE_OUTPUT = """
            {
              "reviewStatus": "needs_review",
              "documentType": "patient_copy_prescription",
              "medications": [{
                "id": "med_1",
                "drugName": {"value": "아모잘탄정", "status": "extracted", "confidence": "high"},
                "dose": {"value": {"value": 1, "unit": "정", "rawText": "1정"}, "status": "extracted"},
                "frequencyPerDay": {"value": 1, "status": "extracted"},
                "durationDays": {"value": 30, "status": "extracted"},
                "timingInstruction": {
                  "source": {"value": "아침 식후", "status": "extracted"},
                  "english": "after breakfast"
                }
              }],
              "issues": []
            }""";

    private void assertMapsCorrectly(PrescriptionExtractionResult result) {
        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.NEEDS_REVIEW);
        assertThat(result.documentType()).isEqualTo(DocumentType.PATIENT_COPY_PRESCRIPTION);
        assertThat(result.medications()).hasSize(1);
        assertThat(result.medications().get(0).drugName().value()).isEqualTo("아모잘탄정");
        assertThat(result.medications().get(0).drugName().status()).isEqualTo(FieldStatus.EXTRACTED);
        assertThat(result.medications().get(0).dose().value().unit()).isEqualTo("정");
        assertThat(result.medications().get(0).timingInstruction().english()).isEqualTo("after breakfast");
    }

    @Test
    void camelCase_객체를_그대로_옮긴다() {
        assertMapsCorrectly(mapper.toResult(json(CAMEL_CASE_OUTPUT)));
    }

    @Test
    void JSON을_담은_문자열로_와도_한_겹_벗긴다() {
        assertMapsCorrectly(mapper.toResult(new StringNode(CAMEL_CASE_OUTPUT)));
    }

    @Test
    void snake_case로_와도_옮긴다() {
        JsonNode snake = json("""
                {
                  "review_status": "ready",
                  "document_type": "other",
                  "medications": [],
                  "issues": []
                }""");

        PrescriptionExtractionResult result = mapper.toResult(snake);

        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.READY);
        assertThat(result.documentType()).isEqualTo(DocumentType.OTHER);
    }

    @Test
    void 모르는_필드가_섞여_있어도_실패하지_않는다() {
        JsonNode extra = json("""
                {"reviewStatus":"ready","documentType":"other","medications":[],"issues":[],
                 "debugTrace":{"tokens":123},"modelVersion":"solar-x"}""");

        assertThat(mapper.toResult(extra).reviewStatus()).isEqualTo(ReviewStatus.READY);
    }

    private void assertRejected(JsonNode output) {
        assertThatThrownBy(() -> mapper.toResult(output))
                .isInstanceOf(AnalysisFailedException.class)
                .extracting(e -> ((AnalysisFailedException) e).getFailureCode())
                .isEqualTo(ExtractionFailureCode.INVALID_AGENT_RESPONSE);
    }

    @Test
    void 출력이_비어_있으면_invalid_agent_response() {
        assertRejected(null);
        assertRejected(JSON.nullNode());
    }

    @Test
    void 문자열인데_JSON이_아니면_invalid_agent_response() {
        assertRejected(new StringNode("죄송합니다, 처리할 수 없습니다."));
    }

    @Test
    void 열거형_값이_계약에_없으면_invalid_agent_response() {
        assertRejected(json("""
                {"reviewStatus":"maybe_ok","documentType":"other","medications":[],"issues":[]}"""));
    }
}
