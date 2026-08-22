package com.example.demo.domain.prescription.analyzer.upstage;

import java.util.ArrayList;
import java.util.List;

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
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 에이전트 출력을 우리 결과 타입으로 옮긴다.
 *
 * <p>Studio 에이전트의 output은 결과 객체가 그대로 오지 않고 단계(step) 배열로 온다.
 * 실제 결과는 마지막 단계의 content_parsed / raw_content / content[].text 중 하나에
 * 문자열로 들어 있다. 어디에 담기는지는 에이전트 구성에 따라 달라지므로 후보를 차례로 시도한다.
 *
 * <p>어긋나면 값 없이 구조만 로그에 남긴다. 내용이 처방전이라 통째로 찍을 수 없다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.analyzer", havingValue = "upstage")
public class UpstageResponseMapper {

    private static final ObjectMapper CAMEL_CASE = lenient(JsonMapper.builder()).build();

    private static final ObjectMapper SNAKE_CASE = lenient(JsonMapper.builder())
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    /** 결과 문자열이 담길 수 있는 자리. 앞에 있는 것부터 시도한다. */
    private static final List<String> TEXT_FIELDS = List.of("content_parsed", "raw_content", "text");

    private static final String CODE_FENCE = "```";

    /** 단계배열 → 단계 → content → 파트 → text → 이중 인코딩까지 내려가려면 여유가 필요하다. */
    private static final int MAX_DEPTH = 10;

    /**
     * 이 길이를 넘지 않는 문자열은 분석 결과일 수 없으므로 내용을 그대로 남긴다.
     * 업스트림이 오류나 거절 문구를 보낼 때 그게 뭔지 알아야 고칠 수 있다.
     */
    private static final int SAFE_TO_LOG_LENGTH = 120;

    private static <B extends MapperBuilder<?, B>> B lenient(B builder) {
        // 에이전트가 우리가 모르는 필드를 더 얹어도 그것 때문에 실패하지는 않는다.
        return builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public PrescriptionExtractionResult toResult(JsonNode output) {
        if (output == null || output.isNull()) {
            throw new AnalysisFailedException(
                    ExtractionFailureCode.INVALID_AGENT_RESPONSE, "에이전트 출력이 비어 있음");
        }

        List<String> inspected = new ArrayList<>();
        for (JsonNode candidate : candidates(output, inspected)) {
            PrescriptionExtractionResult result = convertEitherNaming(candidate);
            if (isUsable(result)) {
                return result;
            }
        }

        // 겉구조만으로는 "결과가 없다"와 "필드명이 다르다"를 구분할 수 없다.
        // 들여다본 후보마다 무엇이었는지 함께 남긴다. 값이 아니라 형태만이다.
        log.error("에이전트 출력을 해석하지 못했다. 겉구조: {} / 들여다본 곳: {}",
                JsonShape.of(output), inspected);
        throw new AnalysisFailedException(
                ExtractionFailureCode.INVALID_AGENT_RESPONSE, "에이전트 출력 구조 불일치");
    }

    /**
     * 결과가 들어 있을 만한 자리를 모은다.
     *
     * <p>단계 배열은 뒤에서부터 본다. 마지막 단계가 최종 출력이다.
     */
    private List<JsonNode> candidates(JsonNode output, List<String> inspected) {
        List<JsonNode> found = new ArrayList<>();
        collect("output", output, found, inspected, 0);
        return found;
    }

    private void collect(String where, JsonNode node, List<JsonNode> found,
            List<String> inspected, int depth) {

        if (node == null || node.isNull()) {
            inspected.add(where + "=없음");
            return;
        }
        if (depth > MAX_DEPTH) {
            return;
        }
        if (node.isString()) {
            String text = node.stringValue();
            parseJson(text).ifPresentOrElse(
                    // 벗긴 결과가 또 문자열일 수 있다. JSON 문자열 안에 JSON을 한 번 더 넣어
                    // 보내는 경우가 흔해서, 더 못 벗길 때까지 파고든다.
                    parsed -> collect(where + "(벗김)", parsed, found, inspected, depth + 1),
                    () -> inspected.add(where + "=" + describeNonJson(text)));
            return;
        }
        if (node.isArray()) {
            for (int i = node.size() - 1; i >= 0; i--) {
                collect(where + "[" + i + "]", node.get(i), found, inspected, depth + 1);
            }
            return;
        }
        if (node.isObject()) {
            // 이 객체 자체가 결과일 수도 있다.
            found.add(node);
            inspected.add(where + "=" + JsonShape.of(node));
            for (String field : TEXT_FIELDS) {
                collect(where + "." + field, node.get(field), found, inspected, depth + 1);
            }
            collect(where + ".content", node.get("content"), found, inspected, depth + 1);
        }
    }

    private static String describeNonJson(String text) {
        if (text.length() <= SAFE_TO_LOG_LENGTH) {
            return "JSON아닌문자열(" + text.length() + "자): \"" + text + "\"";
        }
        return "JSON아닌문자열(" + text.length() + "자)";
    }

    /** LLM이 결과를 코드 펜스로 감싸 내보내는 경우가 있어 걷어낸다. */
    private java.util.Optional<JsonNode> parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.Optional.empty();
        }
        String text = raw.trim();
        if (text.startsWith(CODE_FENCE)) {
            int start = text.indexOf('\n');
            int end = text.lastIndexOf(CODE_FENCE);
            if (start < 0 || end <= start) {
                return java.util.Optional.empty();
            }
            text = text.substring(start + 1, end).trim();
        }
        try {
            return java.util.Optional.of(CAMEL_CASE.readTree(text));
        } catch (JacksonException e) {
            return java.util.Optional.empty();
        }
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

    private PrescriptionExtractionResult convertEitherNaming(JsonNode payload) {
        PrescriptionExtractionResult result = convert(payload, CAMEL_CASE);
        if (isUsable(result)) {
            return result;
        }
        PrescriptionExtractionResult snakeCased = convert(payload, SNAKE_CASE);
        if (isUsable(snakeCased)) {
            log.info("에이전트 출력이 snake_case로 왔다. Studio 출력 스키마 확인 권장.");
            return snakeCased;
        }
        return null;
    }

    private PrescriptionExtractionResult convert(JsonNode payload, ObjectMapper mapper) {
        if (payload == null || !payload.isObject()) {
            return null;
        }
        try {
            return mapper.treeToValue(payload, PrescriptionExtractionResult.class);
        } catch (JacksonException e) {
            return null;
        }
    }
}
