package com.example.demo.domain.prescription.analyzer.upstage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import com.example.demo.domain.prescription.analyzer.ExtractionResultValidator;
import com.example.demo.domain.prescription.dto.result.FieldStatus;
import com.example.demo.domain.prescription.dto.result.PrescriptionExtractionResult;
import com.example.demo.domain.prescription.dto.result.ReviewStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * docs/upstage-agent-output.schema.json 을 Studio 에이전트 출력 스키마로 걸었을 때,
 * 그대로 온 응답이 우리 계약을 통과하는지 확인한다.
 *
 * <p>스키마 파일과 Java 타입이 따로 놀면 배포하고 나서야 알게 되므로 여기서 묶어둔다.
 * 스키마를 고치면 이 샘플도 함께 고쳐야 한다.
 */
class AgentSchemaContractTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final UpstageResponseMapper mapper = new UpstageResponseMapper();
    private final ExtractionResultValidator validator = new ExtractionResultValidator();

    /**
     * 스키마를 엄격 모드로 걸면 선택 필드도 required에 넣고 null을 허용해야 한다.
     * 그래서 실제 응답에는 값 없는 필드가 null로 전부 들어온다. 그 형태를 그대로 재현한다.
     */
    private static final String SCHEMA_CONFORMING_OUTPUT = """
            {
              "reviewStatus": "needs_review",
              "documentType": "patient_copy_prescription",
              "medications": [
                {
                  "id": "med_1",
                  "drugName": {
                    "value": "아모잘탄정 5/50mg",
                    "status": "extracted",
                    "confidence": "high",
                    "evidence": {
                      "page": 1,
                      "coordinates": [{"x": 0.12, "y": 0.34}, {"x": 0.48, "y": 0.34}],
                      "rawText": "아모잘탄정 5/50mg"
                    }
                  },
                  "printedProductCode": {"value": null, "status": "not_present", "confidence": null, "evidence": null},
                  "strength": {"value": {"value": 5, "unit": "mg", "rawText": "5/50mg"}, "status": "extracted", "confidence": "high", "evidence": null},
                  "dosageForm": {"value": "정", "status": "extracted", "confidence": "high", "evidence": null},
                  "dose": {"value": {"value": 1, "unit": "정", "rawText": "1정"}, "status": "extracted", "confidence": "high", "evidence": null},
                  "frequencyPerDay": {"value": 1, "status": "extracted", "confidence": "high", "evidence": null},
                  "durationDays": {"value": 30, "status": "extracted", "confidence": "low", "evidence": null},
                  "timingInstruction": {
                    "source": {"value": "아침 식후 30분", "status": "extracted", "confidence": "high", "evidence": null},
                    "english": "30 minutes after breakfast"
                  },
                  "route": {"value": null, "status": "not_present", "confidence": null, "evidence": null},
                  "asNeeded": {"value": false, "status": "extracted", "confidence": "high", "evidence": null}
                }
              ],
              "issues": [
                {
                  "code": "low_confidence",
                  "medicationId": "med_1",
                  "field": "durationDays",
                  "message": "투약 일수를 확인해 주세요.",
                  "evidence": null
                }
              ]
            }""";

    /** 에이전트가 실제로 돌려주는 봉투. 결과는 content[0].text 안에 JSON 문자열로 들어 있다. */
    private static JsonNode wrappedInAgentEnvelope(String payload) {
        ObjectNode step = JSON.createObjectNode();
        step.put("id", "step-1");
        step.put("type", "message");
        step.put("status", "completed");
        step.putNull("raw_content");
        step.putNull("content_parsed");

        ObjectNode part = step.putArray("content").addObject();
        part.put("type", "output_text");
        part.put("text", payload);

        return JSON.createArrayNode().add(step);
    }

    @Test
    void 스키마대로_온_응답은_우리_타입으로_옮겨진다() {
        PrescriptionExtractionResult result =
                mapper.toResult(wrappedInAgentEnvelope(SCHEMA_CONFORMING_OUTPUT));

        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.NEEDS_REVIEW);
        assertThat(result.medications()).hasSize(1);
        assertThat(result.medications().get(0).drugName().value()).isEqualTo("아모잘탄정 5/50mg");
        assertThat(result.medications().get(0).dose().value().unit()).isEqualTo("정");
        assertThat(result.medications().get(0).durationDays().value()).isEqualTo(30);
        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0).field()).isEqualTo("durationDays");
    }

    @Test
    void 스키마대로_온_응답은_검증도_통과한다() {
        PrescriptionExtractionResult result =
                mapper.toResult(wrappedInAgentEnvelope(SCHEMA_CONFORMING_OUTPUT));

        assertThatCode(() -> validator.validate(result)).doesNotThrowAnyException();
    }

    @Test
    void 값이_없는_선택_필드는_null_status로_들어온다() {
        PrescriptionExtractionResult result =
                mapper.toResult(wrappedInAgentEnvelope(SCHEMA_CONFORMING_OUTPUT));

        // 엄격 모드 스키마라 없는 값도 필드 자체는 온다. status로 구분된다.
        assertThat(result.medications().get(0).route().status()).isEqualTo(FieldStatus.NOT_PRESENT);
        assertThat(result.medications().get(0).route().value()).isNull();
    }

    @Test
    void 좌표와_원문이_보존된다() {
        PrescriptionExtractionResult result =
                mapper.toResult(wrappedInAgentEnvelope(SCHEMA_CONFORMING_OUTPUT));

        var evidence = result.medications().get(0).drugName().evidence();
        assertThat(evidence.page()).isEqualTo(1);
        assertThat(evidence.coordinates()).hasSize(2);
        assertThat(evidence.coordinates().get(0).x()).isEqualTo(0.12);
        assertThat(evidence.rawText()).isEqualTo("아모잘탄정 5/50mg");
    }
}
