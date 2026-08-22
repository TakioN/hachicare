package com.example.demo.domain.prescription.analyzer;

import com.example.demo.domain.prescription.entity.ExtractionFailureCode;

import lombok.Getter;

@Getter
public class AnalysisFailedException extends RuntimeException {

    private final ExtractionFailureCode failureCode;

    public AnalysisFailedException(ExtractionFailureCode failureCode, String detail) {
        super(detail);
        this.failureCode = failureCode;
    }

    public AnalysisFailedException(ExtractionFailureCode failureCode, String detail, Throwable cause) {
        super(detail, cause);
        this.failureCode = failureCode;
    }
}
