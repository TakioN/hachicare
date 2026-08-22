package com.example.demo.domain.prescription.service;

import org.springframework.stereotype.Component;

import com.example.demo.domain.prescription.dto.ExtractionFailureResponse;
import com.example.demo.domain.prescription.dto.ExtractionJobResponse;
import com.example.demo.domain.prescription.entity.PrescriptionExtraction;
import com.example.demo.global.exception.CustomException;
import com.example.demo.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractionJobMapper {

    private final ObjectMapper objectMapper;

    public ExtractionJobResponse toResponse(PrescriptionExtraction extraction) {
        return new ExtractionJobResponse(
                extraction.getPublicId(),
                extraction.getStatus(),
                extraction.getCreatedAt(),
                extraction.getCompletedAt(),
                readResult(extraction),
                ExtractionFailureResponse.from(extraction));
    }

    /**
     * 저장된 결과를 문자열이 아니라 JSON 객체로 싣기 위해 파싱한다.
     * 워커가 검증한 뒤에만 저장하므로 여기서 실패하면 우리 쪽 버그다.
     */
    private JsonNode readResult(PrescriptionExtraction extraction) {
        String resultJson = extraction.getResultJson();
        if (resultJson == null) {
            return null;
        }
        try {
            return objectMapper.readTree(resultJson);
        } catch (JacksonException e) {
            log.error("저장된 분석 결과를 읽지 못했다: id={}", extraction.getPublicId(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
