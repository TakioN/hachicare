package com.example.demo.domain.prescription.analyzer.upstage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.JsonNode;

/**
 * /v2/responses 작업 봉투에서 우리가 쓰는 부분만.
 *
 * <p>model, input, steps는 읽지 않는다. 원본 응답을 그대로 노출하지 않기 위해서이기도 하고,
 * steps에는 중간 단계의 원문이 통째로 들어 있어 붙들고 있을 이유가 없다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpstageResponse(
    String id,
    String status,
    JsonNode output
) {
    private static final String QUEUED = "queued";
    private static final String IN_PROGRESS = "in_progress";
    private static final String COMPLETED = "completed";
    private static final String FAILED = "failed";

    public boolean isPending() {
        return QUEUED.equalsIgnoreCase(status) || IN_PROGRESS.equalsIgnoreCase(status);
    }

    public boolean isCompleted() {
        return COMPLETED.equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return FAILED.equalsIgnoreCase(status);
    }

    public boolean isTerminal() {
        return isCompleted() || isFailed();
    }

    public boolean hasKnownStatus() {
        return isPending() || isTerminal();
    }
}
