package com.example.demo.domain.prescription.dto.result;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** completed는 기술적 처리가 끝났다는 뜻일 뿐이고, 결과를 바로 쓸 수 있는지는 이 값으로 구분한다. */
@AllArgsConstructor
@Getter
public enum ReviewStatus {
    READY("ready"),
    NEEDS_REVIEW("needs_review"),
    NEEDS_RETAKE("needs_retake"),
    UNSUPPORTED_DOCUMENT("unsupported_document");

    @JsonValue
    private final String value;
}
