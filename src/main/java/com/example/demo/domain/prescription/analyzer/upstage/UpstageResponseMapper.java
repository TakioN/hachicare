package com.example.demo.domain.prescription.analyzer.upstage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.demo.domain.prescription.analyzer.AnalysisFailedException;
import com.example.demo.domain.prescription.dto.result.PrescriptionExtractionResult;
import com.example.demo.domain.prescription.entity.ExtractionFailureCode;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.PropertyNamingStrategies;

/**
 * 에이전트 출력을 우리 결과 타입으로 옮긴다.
 *
 * <p>Studio에서 정의한 출력 스키마에 따라 두 가지가 달라질 수 있어 양쪽을 모두 받아들인다.
 * <ul>
 *   <li>output이 객체인지, JSON을 담은 문자열인지
 *   <li>필드가 camelCase인지 snake_case인지
 * </ul>
 * 어긋나면 값 없이 구조만 로그에 남긴다. 내용이 처방전이라 통째로 찍을 수 없다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.analyzer", havingValue = "upstage")
public class UpstageResponseMapper {

    private static final ObjectMapper CAMEL_CASE = lenient(JsonMapper.builder()).build();

    private static final ObjectMapper SNAKE_CASE = lenient(JsonMapper.builder())
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private static <B extends MapperBuilder<?, B>> B lenient(B builder) {
        // 에이전트가 우리가 모르는 필드를 더 얹어도 그것 때문에 실패하지는 않는다.
        return builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public PrescriptionExtractionResult toResult(JsonNode output) {
        JsonNode payload = unwrap(output);

        PrescriptionExtractionResult result = convert(payload, CAMEL_CASE);

        if (!isUsable(result)) {
            PrescriptionExtractionResult snakeCased = convert(payload, SNAKE_CASE);
            if (isUsable(snakeCased)) {
                log.info("에이전트 출력이 snake_case로 왔다. Studio 출력 스키마 확인 권장.");
                result = snakeCased;
            }
        }

        if (!isUsable(result)) {
            log.error("에이전트 출력을 해석하지 못했다. 실제 구조: {}", JsonShape.of(payload));
            throw new AnalysisFailedException(
                    ExtractionFailureCode.INVALID_AGENT_RESPONSE, "에이전트 출력 구조 불일치");
        }
        return result;
    }

    /**
     * 필드 이름이 안 맞으면 Jackson은 예외를 던지지 않고 그 필드를 그냥 null로 둔다.
     * 그래서 "예외가 안 났다"를 성공으로 볼 수 없다. 필수 필드가 실제로 채워졌는지로 판단한다.
     */
    private static boolean isUsable(PrescriptionExtractionResult result) {
        return result != null
                && result.reviewStatus() != null
                && result.documentType() != null
                && result.medications() != null
                && result.issues() != null;
    }

    /** output이 JSON을 담은 문자열로 오는 경우가 있어 한 겹 벗겨본다. */
    private JsonNode unwrap(JsonNode output) {
        if (output == null || output.isNull()) {
            throw new AnalysisFailedException(
                    ExtractionFailureCode.INVALID_AGENT_RESPONSE, "에이전트 출력이 비어 있음");
        }
        if (!output.isString()) {
            return output;
        }
        try {
            return CAMEL_CASE.readTree(output.stringValue());
        } catch (JacksonException e) {
            throw new AnalysisFailedException(
                    ExtractionFailureCode.INVALID_AGENT_RESPONSE, "문자열 출력이 JSON이 아님", e);
        }
    }

    private PrescriptionExtractionResult convert(JsonNode payload, ObjectMapper mapper) {
        try {
            return mapper.treeToValue(payload, PrescriptionExtractionResult.class);
        } catch (JacksonException e) {
            return null;
        }
    }
}
