package com.example.demo.domain.prescription.analyzer.upstage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.demo.domain.prescription.analyzer.AnalysisFailedException;
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
import com.example.demo.domain.prescription.dto.result.SourceCoordinate;
import com.example.demo.domain.prescription.dto.result.SourceEvidence;
import com.example.demo.domain.prescription.dto.result.TimingInstruction;
import com.example.demo.domain.prescription.entity.ExtractionFailureCode;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Maps the observed Upstage Studio workflow response into the backend-owned DTO. */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.analyzer", havingValue = "upstage")
public class UpstageResponseMapper {

    private static final String CLASSIFY_STEP = "step_2_classify";
    private static final String EXTRACT_STEP = "Information Extract - prescription_medications";
    private static final String INSTRUCT_STEP = "Instruct - Translate timing";
    private static final String VALIDATE_STEP = "Validate - Validate medication plan";
    private static final String PRESCRIPTION = "patient_copy_prescription";
    private static final Pattern CITATION_MARKER = Pattern.compile("【†(\\d+)】");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public PrescriptionExtractionResult toResult(JsonNode output) {
        try {
            requireArray(output, "output");

            JsonNode classify = requireStepContent(output, CLASSIFY_STEP);
            String documentType = requireText(decode(classify.get("text")), "classify text");
            JsonNode classifyMetadata = requireObject(
                    decode(classify.get("additional_values")), "classify additional_values");
            JsonNode classifiedDocument = requireObject(
                    classifyMetadata.get("document_type"), "classified document metadata");
            if (!documentType.equals(requireText(classifiedDocument.get("_value"), "classified document"))) {
                throw invalid("Classify value and metadata disagree");
            }
            if ("other".equals(documentType)) {
                return unsupportedDocument();
            }
            if (!PRESCRIPTION.equals(documentType)) {
                throw invalid("Unknown document type");
            }

            JsonNode extract = requireStepContent(output, EXTRACT_STEP);
            JsonNode extractValues = requireObject(decode(extract.get("text")), "extract text");
            JsonNode extractMetadata = requireObject(
                    decode(extract.get("additional_values")), "extract additional_values");
            JsonNode medicationValues = requireArray(extractValues.get("medications"), "medications");
            JsonNode medicationMetadata = requireArray(
                    extractMetadata.get("medications"), "medication metadata");
            if (medicationValues.size() == 0 || medicationValues.size() != medicationMetadata.size()) {
                throw invalid("Medication values and metadata do not match");
            }

            JsonNode instruct = requireStepContent(output, INSTRUCT_STEP);
            Map<Integer, String> translations = parseTranslations(instruct, medicationValues.size());

            JsonNode validate = requireStepContent(output, VALIDATE_STEP);
            JsonNode validation = requireObject(
                    decode(validate.get("additional_values")), "validate additional_values");
            String verdict = requireText(validation.get("verdict"), "verdict");
            String verdictText = requireText(decode(validate.get("text")), "validate text");
            if (!verdict.equals(verdictText)) {
                throw invalid("Validate text and verdict disagree");
            }
            ReviewStatus baseStatus = switch (verdict) {
                case "green" -> ReviewStatus.READY;
                case "yellow" -> ReviewStatus.NEEDS_REVIEW;
                case "red" -> ReviewStatus.NEEDS_RETAKE;
                default -> throw invalid("Unknown validation verdict");
            };

            List<MedicationExtraction> medications = new ArrayList<>();
            List<ExtractionIssue> issues = new ArrayList<>();
            boolean criticalReviewRequired = false;
            for (int index = 0; index < medicationValues.size(); index++) {
                MappedMedication mapped = mapMedication(
                        index,
                        medicationValues.get(index),
                        medicationMetadata.get(index),
                        translations.get(index),
                        baseStatus == ReviewStatus.NEEDS_RETAKE);
                medications.add(mapped.medication());
                issues.addAll(mapped.issues());
                criticalReviewRequired |= mapped.criticalReviewRequired();
            }

            ReviewStatus reviewStatus = baseStatus == ReviewStatus.READY && criticalReviewRequired
                    ? ReviewStatus.NEEDS_REVIEW
                    : baseStatus;
            return new PrescriptionExtractionResult(
                    reviewStatus, DocumentType.PATIENT_COPY_PRESCRIPTION, medications, issues);
        } catch (AnalysisFailedException exception) {
            log.error("Upstage output rejected. Shape: {}", JsonShape.of(output));
            throw exception;
        }
    }

    private MappedMedication mapMedication(
            int index,
            JsonNode values,
            JsonNode metadata,
            String translation,
            boolean unreadableMissing) {
        requireObject(values, "medication");
        requireObject(metadata, "medication metadata");

        String medicationId = "med_" + (index + 1);
        FieldStatus missingStatus = unreadableMissing ? FieldStatus.UNREADABLE : FieldStatus.NOT_PRESENT;

        ExtractedField<String> drugName = stringField(
                values.get("printed_drug_name"), metadata.get("printed_drug_name"), missingStatus);
        ExtractedField<String> printedProductCode = stringField(
                values.get("printed_product_code"), metadata.get("printed_product_code"),
                FieldStatus.NOT_PRESENT);
        ExtractedField<Quantity> strength = quantityField(
                values.get("strength_value"), values.get("strength_unit"),
                metadata.get("strength_value"), metadata.get("strength_unit"),
                FieldStatus.NOT_PRESENT);
        ExtractedField<String> dosageForm = stringField(
                values.get("dosage_form"), metadata.get("dosage_form"), FieldStatus.NOT_PRESENT);
        ExtractedField<Quantity> dose = quantityField(
                values.get("dose_value"), values.get("dose_unit"),
                metadata.get("dose_value"), metadata.get("dose_unit"), missingStatus);
        ExtractedField<Integer> frequency = integerField(
                values.get("frequency_per_day"), metadata.get("frequency_per_day"), missingStatus);
        ExtractedField<Integer> duration = integerField(
                values.get("duration_days"), metadata.get("duration_days"), missingStatus);
        ExtractedField<String> timingSource = stringField(
                values.get("timing_instruction_ko"), metadata.get("timing_instruction_ko"),
                missingStatus);
        ExtractedField<String> route = stringField(
                values.get("route"), metadata.get("route"), FieldStatus.NOT_PRESENT);
        ExtractedField<Boolean> asNeeded = new ExtractedField<>(
                null, FieldStatus.NOT_PRESENT, null, null);

        List<ExtractionIssue> issues = new ArrayList<>();
        addIssue(issues, medicationId, "drugName", drugName, true);
        addIssue(issues, medicationId, "dose", dose, true);
        addIssue(issues, medicationId, "frequencyPerDay", frequency, true);
        addIssue(issues, medicationId, "durationDays", duration, false);
        addIssue(issues, medicationId, "timingInstruction", timingSource, false);

        boolean criticalReviewRequired = requiresReview(drugName)
                || requiresReview(dose)
                || requiresReview(frequency);
        MedicationExtraction medication = new MedicationExtraction(
                medicationId,
                drugName,
                printedProductCode,
                strength,
                dosageForm,
                dose,
                frequency,
                duration,
                new TimingInstruction(timingSource, translation),
                route,
                asNeeded);
        return new MappedMedication(medication, issues, criticalReviewRequired);
    }

    private static PrescriptionExtractionResult unsupportedDocument() {
        ExtractionIssue issue = new ExtractionIssue(
                ExtractionIssueCode.UNSUPPORTED_DOCUMENT,
                null,
                null,
                "지원하지 않는 문서 형식입니다.",
                null);
        return new PrescriptionExtractionResult(
                ReviewStatus.UNSUPPORTED_DOCUMENT, DocumentType.OTHER, List.of(), List.of(issue));
    }

    private static boolean requiresReview(ExtractedField<?> field) {
        return !field.isExtracted() || field.confidence() == ExtractionConfidence.LOW;
    }

    private static void addIssue(
            List<ExtractionIssue> issues,
            String medicationId,
            String fieldName,
            ExtractedField<?> field,
            boolean critical) {
        if (field.status() == FieldStatus.UNREADABLE) {
            issues.add(new ExtractionIssue(
                    ExtractionIssueCode.UNREADABLE_DOCUMENT,
                    medicationId,
                    fieldName,
                    "문서에서 값을 읽지 못했습니다.",
                    field.evidence()));
        } else if (!field.isExtracted()) {
            issues.add(new ExtractionIssue(
                    ExtractionIssueCode.MISSING_REQUIRED_FIELD,
                    medicationId,
                    fieldName,
                    critical ? "필수 값이 없습니다." : "복약 일정에 필요한 값이 없습니다.",
                    field.evidence()));
        } else if (field.confidence() == ExtractionConfidence.LOW) {
            issues.add(new ExtractionIssue(
                    ExtractionIssueCode.LOW_CONFIDENCE,
                    medicationId,
                    fieldName,
                    "추출 신뢰도가 낮아 확인이 필요합니다.",
                    field.evidence()));
        }
    }

    private ExtractedField<String> stringField(
            JsonNode valueNode,
            JsonNode metadata,
            FieldStatus missingStatus) {
        if (valueNode == null || valueNode.isNull() || !valueNode.isString()) {
            throw invalid("Expected string field");
        }
        requireMatchingEvidenceValue(metadata, valueNode);
        String value = valueNode.stringValue().trim();
        if (value.isEmpty()) {
            return new ExtractedField<>(null, missingStatus, null, null);
        }
        return new ExtractedField<>(
                value,
                FieldStatus.EXTRACTED,
                confidence(metadata),
                evidence(metadata, value));
    }

    private ExtractedField<Integer> integerField(
            JsonNode valueNode,
            JsonNode metadata,
            FieldStatus missingStatus) {
        if (valueNode == null || !valueNode.isIntegralNumber()) {
            throw invalid("Expected integer field");
        }
        requireMatchingEvidenceValue(metadata, valueNode);
        int value = valueNode.intValue();
        if (value <= 0) {
            return new ExtractedField<>(null, missingStatus, null, null);
        }
        return new ExtractedField<>(
                value,
                FieldStatus.EXTRACTED,
                confidence(metadata),
                evidence(metadata, Integer.toString(value)));
    }

    private ExtractedField<Quantity> quantityField(
            JsonNode valueNode,
            JsonNode unitNode,
            JsonNode valueMetadata,
            JsonNode unitMetadata,
            FieldStatus missingStatus) {
        if (valueNode == null || !valueNode.isNumber()
                || unitNode == null || !unitNode.isString()) {
            throw invalid("Expected quantity field");
        }
        requireMatchingEvidenceValue(valueMetadata, valueNode);
        requireMatchingEvidenceValue(unitMetadata, unitNode);
        double value = valueNode.doubleValue();
        String unit = unitNode.stringValue().trim();
        if (value <= 0 || unit.isEmpty()) {
            return new ExtractedField<>(null, missingStatus, null, null);
        }

        String rawText = formatNumber(value) + unit;
        ExtractionConfidence confidence = confidence(valueMetadata) == ExtractionConfidence.LOW
                || confidence(unitMetadata) == ExtractionConfidence.LOW
                ? ExtractionConfidence.LOW
                : ExtractionConfidence.HIGH;
        return new ExtractedField<>(
                new Quantity(value, unit, rawText),
                FieldStatus.EXTRACTED,
                confidence,
                evidence(valueMetadata, rawText));
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private ExtractionConfidence confidence(JsonNode metadata) {
        JsonNode object = requireObject(metadata, "field metadata");
        String value = requireText(object.get("confidence"), "confidence");
        return switch (value) {
            case "high" -> ExtractionConfidence.HIGH;
            case "low" -> ExtractionConfidence.LOW;
            default -> throw invalid("Unknown confidence");
        };
    }

    private void requireMatchingEvidenceValue(JsonNode metadata, JsonNode value) {
        JsonNode object = requireObject(metadata, "field metadata");
        JsonNode evidenceValue = object.get("_value");
        if (evidenceValue == null) {
            throw invalid("Field metadata has no value");
        }
        boolean matches = evidenceValue.isNumber() && value.isNumber()
                ? Double.compare(evidenceValue.doubleValue(), value.doubleValue()) == 0
                : evidenceValue.equals(value);
        if (!matches) {
            throw invalid("Extracted value and evidence disagree");
        }
    }

    private SourceEvidence evidence(JsonNode metadata, String rawText) {
        JsonNode object = requireObject(metadata, "field metadata");
        JsonNode pageNode = object.get("page");
        if (pageNode == null || !pageNode.isIntegralNumber() || pageNode.intValue() <= 0) {
            throw invalid("Invalid evidence page");
        }

        JsonNode words = requireArray(object.get("word_coordinates"), "word coordinates");
        List<SourceCoordinate> coordinates = words.size() == 0
                ? List.of()
                : boundingBox(words);
        return new SourceEvidence(pageNode.intValue(), coordinates, rawText);
    }

    private List<SourceCoordinate> boundingBox(JsonNode words) {
        double minX = 1;
        double minY = 1;
        double maxX = 0;
        double maxY = 0;
        int pointCount = 0;

        for (JsonNode word : words) {
            JsonNode polygon = requireArray(word, "word polygon");
            for (JsonNode point : polygon) {
                JsonNode xNode = point.get("x");
                JsonNode yNode = point.get("y");
                if (xNode == null || !xNode.isNumber() || yNode == null || !yNode.isNumber()) {
                    throw invalid("Invalid evidence coordinate");
                }
                double x = xNode.doubleValue();
                double y = yNode.doubleValue();
                if (x < 0 || x > 1 || y < 0 || y > 1) {
                    throw invalid("Evidence coordinate is outside 0..1");
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                pointCount++;
            }
        }
        if (pointCount == 0) {
            return List.of();
        }
        return List.of(
                new SourceCoordinate(minX, minY),
                new SourceCoordinate(maxX, minY),
                new SourceCoordinate(maxX, maxY),
                new SourceCoordinate(minX, maxY));
    }

    private Map<Integer, String> parseTranslations(JsonNode instruct, int medicationCount) {
        String text = requireText(decode(instruct.get("text")), "instruct text");
        JsonNode metadata = requireObject(
                decode(instruct.get("additional_values")), "instruct additional_values");
        Set<Integer> citationIndexes = new HashSet<>();
        JsonNode citations = requireArray(metadata.get("citations"), "citations");
        for (JsonNode citation : citations) {
            JsonNode index = citation.get("index");
            if (index != null && index.isIntegralNumber()) {
                citationIndexes.add(index.intValue());
            }
        }

        Matcher marker = CITATION_MARKER.matcher(text);
        StringBuffer cleaned = new StringBuffer();
        while (marker.find()) {
            if (!citationIndexes.contains(Integer.parseInt(marker.group(1)))) {
                return Map.of();
            }
            marker.appendReplacement(cleaned, "");
        }
        marker.appendTail(cleaned);

        String json = extractJson(cleaned.toString());
        if (json == null) {
            return Map.of();
        }
        try {
            JsonNode payload = JSON.readTree(json);
            if (!payload.isObject() || payload.size() != 1 || !payload.has("translations")) {
                return Map.of();
            }
            JsonNode translations = payload.get("translations");
            if (!translations.isArray()) {
                return Map.of();
            }

            Map<Integer, String> result = new HashMap<>();
            for (JsonNode translation : translations) {
                if (!translation.isObject() || translation.size() != 2
                        || !translation.has("row_index")
                        || !translation.has("timing_instruction_en")) {
                    return Map.of();
                }
                JsonNode rowIndex = translation.get("row_index");
                JsonNode english = translation.get("timing_instruction_en");
                if (!rowIndex.isIntegralNumber() || !english.isString()) {
                    return Map.of();
                }
                int index = rowIndex.intValue();
                String value = english.stringValue().trim();
                if (index < 0 || index >= medicationCount || value.isEmpty()
                        || result.put(index, value) != null) {
                    return Map.of();
                }
            }
            return result;
        } catch (JacksonException exception) {
            return Map.of();
        }
    }

    private static String extractJson(String value) {
        String trimmed = value.trim();
        String prefix = "```json";
        if (!trimmed.startsWith(prefix)) {
            return trimmed;
        }
        if (!trimmed.endsWith("```")) {
            return null;
        }
        return trimmed.substring(prefix.length(), trimmed.length() - 3).trim();
    }

    private JsonNode requireStepContent(JsonNode output, String model) {
        JsonNode found = null;
        for (JsonNode step : output) {
            JsonNode stepModel = step.get("model");
            if (!step.isObject() || stepModel == null || !stepModel.isString()
                    || !model.equals(stepModel.stringValue())) {
                continue;
            }
            if (found != null) {
                throw invalid("Duplicate workflow step");
            }
            JsonNode stepType = step.get("type");
            JsonNode stepStatus = step.get("status");
            if (stepType == null || !stepType.isString() || !"message".equals(stepType.stringValue())
                    || stepStatus == null || !stepStatus.isString()
                    || !"completed".equals(stepStatus.stringValue())) {
                throw invalid("Workflow step is not completed");
            }
            JsonNode content = requireArray(step.get("content"), model + " content");
            JsonNode contentType = content.size() == 1 ? content.get(0).get("type") : null;
            if (content.size() != 1 || !content.get(0).isObject()
                    || contentType == null || !contentType.isString()
                    || !"output_text".equals(contentType.stringValue())) {
                throw invalid("Invalid workflow step content");
            }
            found = content.get(0);
        }
        if (found == null) {
            throw invalid("Missing workflow step");
        }
        return found;
    }

    private JsonNode decode(JsonNode value) {
        if (value == null || value.isNull()) {
            throw invalid("Missing encoded value");
        }
        JsonNode current = value;
        for (int index = 0; index < 3 && current.isString(); index++) {
            try {
                JsonNode decoded = JSON.readTree(current.stringValue());
                if (decoded == null) {
                    break;
                }
                current = decoded;
            } catch (JacksonException exception) {
                break;
            }
        }
        return current;
    }

    private JsonNode requireArray(JsonNode value, String name) {
        if (value == null || !value.isArray()) {
            throw invalid(name + " must be an array");
        }
        return value;
    }

    private JsonNode requireObject(JsonNode value, String name) {
        if (value == null || !value.isObject()) {
            throw invalid(name + " must be an object");
        }
        return value;
    }

    private String requireText(JsonNode value, String name) {
        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            throw invalid(name + " must be text");
        }
        return value.stringValue();
    }

    private static AnalysisFailedException invalid(String message) {
        return new AnalysisFailedException(ExtractionFailureCode.INVALID_AGENT_RESPONSE, message);
    }

    private record MappedMedication(
        MedicationExtraction medication,
        List<ExtractionIssue> issues,
        boolean criticalReviewRequired
    ) {
    }
}
