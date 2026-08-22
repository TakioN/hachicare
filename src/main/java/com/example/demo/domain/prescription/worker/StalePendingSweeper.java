package com.example.demo.domain.prescription.worker;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.global.storage.StorageService;

import lombok.RequiredArgsConstructor;

/**
 * pending에 갇힌 작업을 회수한다.
 *
 * <p>워커가 죽거나, 큐가 차서 작업이 거절되거나, 서버가 재기동해 이벤트가 유실되면
 * 아무도 그 작업을 끝내주지 않는다. 클라이언트는 영원히 폴링하게 되므로
 * 기한을 넘긴 pending은 타임아웃 실패로 확정한다.
 */
@Component
@RequiredArgsConstructor
public class StalePendingSweeper {

    private final ExtractionProcessor processor;
    private final StorageService storageService;

    @Value("${app.extraction.timeout:2m}")
    private Duration timeout;

    @Scheduled(fixedDelayString = "${app.extraction.sweep-interval:30s}")
    public void sweep() {
        // 원본 삭제는 실패 상태가 커밋된 뒤에 한다.
        Map<String, String> swept = processor.failStalePending(timeout);

        swept.forEach((publicId, imageKey) -> {
            if (storageService.deleteQuietly(imageKey)) {
                processor.markImageDeleted(publicId);
            }
        });
    }
}
