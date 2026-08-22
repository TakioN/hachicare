package com.example.demo.domain.prescription.entity;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ExtractionFailureCode {
    UPSTREAM_UNAVAILABLE("upstream_unavailable", "처방전을 분석하지 못했습니다."),
    UPSTREAM_TIMEOUT("upstream_timeout", "분석 시간이 초과되었습니다."),
    INVALID_AGENT_RESPONSE("invalid_agent_response", "분석 결과를 해석하지 못했습니다."),
    INTERNAL_ERROR("internal_error", "처방전을 분석하지 못했습니다.");

    @JsonValue
    private final String value;

    /** 사용자에게 그대로 보여줄 기본 문구. 업스트림 원본 오류 메시지는 노출하지 않는다. */
    private final String defaultMessage;
}
