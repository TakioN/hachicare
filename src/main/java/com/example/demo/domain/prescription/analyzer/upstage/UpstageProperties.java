package com.example.demo.domain.prescription.analyzer.upstage;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Upstage Studio 에이전트 접속 설정.
 *
 * <p>agentId는 Studio에서 만든 에이전트의 식별자로, 요청 본문의 {@code model} 필드로 들어간다.
 * 엔드포인트는 모든 에이전트가 공유한다.
 */
@ConfigurationProperties(prefix = "upstage")
public record UpstageProperties(
    String baseUrl,
    String apiKey,
    String agentId,
    /** 개별 HTTP 호출 하나의 타임아웃. */
    Duration requestTimeout,
    /** 작업 완료를 기다리며 다시 물어보는 간격. */
    Duration pollInterval,
    /** 이 시간을 넘기면 기다리기를 포기하고 upstream_timeout으로 확정한다. */
    Duration pollTimeout
) {
}
