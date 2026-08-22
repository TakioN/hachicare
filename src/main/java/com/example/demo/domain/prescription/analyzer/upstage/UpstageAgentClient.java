package com.example.demo.domain.prescription.analyzer.upstage;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.demo.domain.prescription.analyzer.AnalysisFailedException;
import com.example.demo.domain.prescription.entity.ExtractionFailureCode;

import lombok.extern.slf4j.Slf4j;

/**
 * Upstage Agent API(/v2) 호출.
 *
 * <p>워커 스레드에서 블로킹으로 도는 경로라 RestClient를 쓴다.
 * 전송 계층의 실패만 다루고, 결과 해석은 {@link UpstageResponseMapper}가 맡는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.analyzer", havingValue = "upstage")
public class UpstageAgentClient {

    private final RestClient restClient;
    private final UpstageProperties properties;

    public UpstageAgentClient(
            @Qualifier("upstageRestClient") RestClient restClient, UpstageProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /** 원본을 업스트림에 올리고 파일 식별자를 받는다. */
    public String uploadFile(byte[] content, String filename, MediaType contentType) {
        // 파트 헤더에 직접 실어야 한다. Resource만 넘기면 확장자 추론에 맡겨지고,
        // HEIC처럼 알려지지 않은 확장자는 application/octet-stream으로 나간다.
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(contentType);

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new HttpEntity<>(new NamedByteArrayResource(content, filename), partHeaders));
        form.add("purpose", "user_data");

        UpstageFile uploaded = exchange("파일 업로드", () -> restClient.post()
                .uri("/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(UpstageFile.class));

        if (uploaded == null || uploaded.id() == null) {
            throw new AnalysisFailedException(
                    ExtractionFailureCode.INVALID_AGENT_RESPONSE, "파일 업로드 응답에 id가 없음");
        }
        return uploaded.id();
    }

    /** 에이전트 실행을 요청하고 작업 식별자를 받는다. 결과는 폴링으로 가져온다. */
    public UpstageResponse createResponse(String fileId) {
        Map<String, Object> request = Map.of(
                "model", properties.agentId(),
                "input", List.of(Map.of(
                        "role", "user",
                        "content", List.of(Map.of(
                                "type", "input_file",
                                "file_id", fileId)))),
                // 마지막 단계 출력만 받는다. all이면 중간 단계 원문까지 딸려온다.
                "include", List.of("last"));

        return requireResponse(exchange("에이전트 실행", () -> restClient.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(UpstageResponse.class)));
    }

    public UpstageResponse getResponse(String responseId) {
        return requireResponse(exchange("작업 조회", () -> restClient.get()
                .uri("/responses/{id}", responseId)
                .retrieve()
                .body(UpstageResponse.class)));
    }

    private static UpstageResponse requireResponse(UpstageResponse response) {
        if (response == null || response.id() == null || response.status() == null) {
            throw new AnalysisFailedException(
                    ExtractionFailureCode.INVALID_AGENT_RESPONSE, "작업 응답에 id나 status가 없음");
        }
        return response;
    }

    /**
     * 전송 실패를 실패 코드로 옮긴다. 원인 예외 메시지에는 업스트림 본문이 섞일 수 있으므로
     * 사용자에게 나가는 문구로 쓰지 않고 로그에만 남긴다.
     */
    private <T> T exchange(String operation, ThrowingSupplier<T> call) {
        try {
            return call.get();
        } catch (AnalysisFailedException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("Upstage {} 실패", operation, e);
            throw new AnalysisFailedException(
                    ExtractionFailureCode.UPSTREAM_UNAVAILABLE, operation + " 실패", e);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get();
    }

    /** ByteArrayResource는 파일명이 없다. multipart 파트에 filename을 실으려면 붙여줘야 한다. */
    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] content, String filename) {
            super(content);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
