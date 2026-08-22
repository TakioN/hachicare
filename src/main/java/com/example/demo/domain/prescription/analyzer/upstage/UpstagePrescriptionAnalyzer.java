package com.example.demo.domain.prescription.analyzer.upstage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.example.demo.domain.prescription.analyzer.AnalysisFailedException;
import com.example.demo.domain.prescription.analyzer.PrescriptionAnalyzer;
import com.example.demo.domain.prescription.dto.result.PrescriptionExtractionResult;
import com.example.demo.domain.prescription.entity.ExtractionFailureCode;
import com.example.demo.global.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Upstage Studio 에이전트로 처방전을 분석한다.
 *
 * <p>업스트림이 비동기라 업로드 → 실행 → 폴링 순으로 진행한다. 폴링은 워커 스레드를 붙든 채
 * 도므로 pollTimeout으로 상한을 둔다. 업스트림 대기가 길어지는 환경이라면 스레드를 놓아주고
 * 별도 스케줄러가 확인하는 구조로 바꿔야 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.analyzer", havingValue = "upstage")
@RequiredArgsConstructor
public class UpstagePrescriptionAnalyzer implements PrescriptionAnalyzer {

    private final StorageService storageService;
    private final UpstageAgentClient client;
    private final UpstageResponseMapper responseMapper;
    private final UpstageProperties properties;
    private final Clock clock;

    @Override
    public PrescriptionExtractionResult analyze(String imageKey) {
        String filename = filenameOf(imageKey);
        byte[] content = storageService.download(imageKey);

        String fileId = client.uploadFile(content, filename, contentTypeOf(filename));
        RuntimeException analysisFailure = null;
        try {
            UpstageResponse created = client.createResponse(fileId);
            UpstageResponse finished = awaitCompletion(created);
            if (finished.isFailed()) {
                throw new AnalysisFailedException(
                        ExtractionFailureCode.UPSTREAM_UNAVAILABLE,
                        "에이전트가 실패로 종료: responseId=" + finished.id());
            }
            return responseMapper.toResult(finished.output());
        } catch (RuntimeException exception) {
            analysisFailure = exception;
            throw exception;
        } finally {
            try {
                client.deleteFile(fileId);
            } catch (RuntimeException cleanupFailure) {
                if (analysisFailure == null) {
                    throw cleanupFailure;
                }
                analysisFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    private UpstageResponse awaitCompletion(UpstageResponse created) {
        Instant deadline = Instant.now(clock).plus(properties.pollTimeout());

        UpstageResponse current = created;
        while (current.isPending()) {
            if (Instant.now(clock).isAfter(deadline)) {
                throw new AnalysisFailedException(
                        ExtractionFailureCode.UPSTREAM_TIMEOUT,
                        "에이전트 응답 대기 초과: responseId=" + current.id());
            }
            sleep(properties.pollInterval());
            current = client.getResponse(current.id());
        }
        return current;
    }

    private static void sleep(Duration interval) {
        try {
            Thread.sleep(interval.toMillis());
        } catch (InterruptedException e) {
            // 종료 중이다. 인터럽트 상태를 되살려 상위가 알 수 있게 한다.
            Thread.currentThread().interrupt();
            throw new AnalysisFailedException(
                    ExtractionFailureCode.INTERNAL_ERROR, "폴링 중 인터럽트", e);
        }
    }

    /** 오브젝트 키는 prescriptions/{publicId}.{ext} 형태다. 뒷부분만 파일명으로 쓴다. */
    private static String filenameOf(String imageKey) {
        return imageKey.substring(imageKey.lastIndexOf('/') + 1);
    }

    /** 저장 시 컨텐츠 타입을 따로 보관하지 않으므로 확장자로 되돌린다. */
    private static MediaType contentTypeOf(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "heic", "heif" -> MediaType.parseMediaType("image/" + extension);
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
