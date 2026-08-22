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
    Duration pollTimeout,
    /**
     * 어느 단계의 출력을 받을지. all이면 전 단계, last면 마지막 단계만.
     * 마지막 단계가 마무리 메시지이고 구조화된 결과는 앞 단계에 있는 구성이 흔해서 all이 안전하다.
     */
    String includeSteps,
    /**
     * 비어 있지 않으면 업스트림 원본 응답을 이 디렉터리에 그대로 떨군다. 진단용이다.
     * 파일에 처방전 내용이 그대로 들어가므로 운영에서는 반드시 비워 둘 것.
     */
    String rawResponseDumpDir
) {
}
