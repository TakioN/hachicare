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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
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

    /**
     * 실제로 관측된 Studio 에이전트 출력 형태. 결과 객체가 아니라 단계 배열이고,
     * 결과는 그 안 어딘가에 문자열로 들어 있다.
     */
    private static ObjectNode step(String payloadField, String payload) {
        ObjectNode node = JSON.createObjectNode();
        node.put("id", "step-1");
        node.put("type", "message");
        node.put("status", "completed");
        node.put("model", "agt_x");
        node.put("role", "assistant");
        node.putNull("task");
        node.putArray("sources");

        ObjectNode part = node.putArray("content").addObject();
        part.put("type", "output_text");
        part.putArray("annotations");

        if ("text".equals(payloadField)) {
            part.put("text", payload);
        } else {
            part.putNull("text");
            node.put(payloadField, payload);
        }
        return node;
    }

    private static ArrayNode steps(ObjectNode... nodes) {
        ArrayNode array = JSON.createArrayNode();
        for (ObjectNode node : nodes) {
            array.add(node);
        }
        return array;
    }

    private void assertMapsCorrectly(PrescriptionExtractionResult result) {
        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.NEEDS_REVIEW);
        assertThat(result.documentType()).isEqualTo(DocumentType.PATIENT_COPY_PRESCRIPTION);
        assertThat(result.medications()).hasSize(1);
        assertThat(result.medications().get(0).drugName().value()).isEqualTo("아모잘탄정");
        assertThat(result.medications().get(0).drugName().status()).isEqualTo(FieldStatus.EXTRACTED);
        assertThat(result.medications().get(0).dose().value().unit()).isEqualTo("정");
        assertThat(result.medications().get(0).timingInstruction().english()).isEqualTo("after breakfast");
    }

    private void assertRejected(JsonNode output) {
        assertThatThrownBy(() -> mapper.toResult(output))
                .isInstanceOf(AnalysisFailedException.class)
                .extracting(e -> ((AnalysisFailedException) e).getFailureCode())
                .isEqualTo(ExtractionFailureCode.INVALID_AGENT_RESPONSE);
    }

    @Test
    void 결과_객체가_그대로_오면_그대로_옮긴다() {
        assertMapsCorrectly(mapper.toResult(json(CAMEL_CASE_OUTPUT)));
    }

    @Test
    void JSON을_담은_문자열로_와도_한_겹_벗긴다() {
        assertMapsCorrectly(mapper.toResult(new StringNode(CAMEL_CASE_OUTPUT)));
    }

    @Test
    void 단계_배열의_content_parsed에서_결과를_찾는다() {
        assertMapsCorrectly(mapper.toResult(steps(step("content_parsed", CAMEL_CASE_OUTPUT))));
    }

    @Test
    void 단계_배열의_raw_content에서_결과를_찾는다() {
        assertMapsCorrectly(mapper.toResult(steps(step("raw_content", CAMEL_CASE_OUTPUT))));
    }

    @Test
    void 단계_배열의_content_text에서_결과를_찾는다() {
        assertMapsCorrectly(mapper.toResult(steps(step("text", CAMEL_CASE_OUTPUT))));
    }

    @Test
    void 코드_펜스로_감싸져_있어도_걷어낸다() {
        String fenced = "```json\n" + CAMEL_CASE_OUTPUT + "\n```";

        assertMapsCorrectly(mapper.toResult(steps(step("text", fenced))));
    }

    @Test
    void 단계가_여럿이면_마지막_단계를_쓴다() {
        String earlier = CAMEL_CASE_OUTPUT.replace("needs_review", "needs_retake");

        PrescriptionExtractionResult result = mapper.toResult(
                steps(step("text", earlier), step("text", CAMEL_CASE_OUTPUT)));

        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.NEEDS_REVIEW);
    }

    @Test
    void JSON_문자열_안에_JSON이_한_번_더_들어_있어도_벗긴다() {
        // 실제로 관측된 형태. content[0].text 를 파싱하면 또 문자열이 나온다.
        String doubleEncoded = JSON.writeValueAsString(CAMEL_CASE_OUTPUT);

        assertMapsCorrectly(mapper.toResult(steps(step("text", doubleEncoded))));
    }

    @Test
    void 세_겹으로_싸여_있어도_벗긴다() {
        String tripleEncoded = JSON.writeValueAsString(JSON.writeValueAsString(CAMEL_CASE_OUTPUT));

        assertMapsCorrectly(mapper.toResult(steps(step("text", tripleEncoded))));
    }

    @Test
    void 벗겨도_JSON이_아니면_invalid_agent_response() {
        String quotedText = JSON.writeValueAsString("처방전을 확인할 수 없습니다.");

        assertRejected(steps(step("text", quotedText)));
    }

    @Test
    void 마지막_단계가_마무리_문구여도_앞_단계에서_결과를_찾는다() {
        // include=all 로 받으면 마지막이 짧은 메시지이고 결과는 앞 단계에 있을 수 있다
        PrescriptionExtractionResult result = mapper.toResult(steps(
                step("text", CAMEL_CASE_OUTPUT),
                step("text", "완료")));

        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.NEEDS_REVIEW);
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

    @Test
    void 출력이_비어_있으면_invalid_agent_response() {
        assertRejected(null);
        assertRejected(JSON.nullNode());
    }

    @Test
    void 어디에도_결과가_없으면_invalid_agent_response() {
        assertRejected(steps(step("text", "그냥 안내 문구입니다")));
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
