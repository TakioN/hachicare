package com.example.demo.domain.prescription.worker;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.demo.domain.prescription.event.ExtractionRequestedEvent;
import com.example.demo.global.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractionWorker {

    private final ExtractionProcessor processor;
    private final StorageService storageService;

    /**
     * 커밋 이후에 받는다. BEFORE_COMMIT이나 일반 리스너로 받으면 워커가 아직 커밋되지 않은
     * 작업을 조회해 "없는 작업"으로 처리해버린다.
     */
    @Async("extractionTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExtractionRequested(ExtractionRequestedEvent event) {
        process(event.publicId());
    }

    /** 스위퍼나 테스트에서 동기적으로 부를 수 있도록 열어둔다. */
    public void process(String publicId) {
        processor.analyzeAndFinish(publicId)
                .ifPresent(imageKey -> deleteOriginal(publicId, imageKey));
    }

    /**
     * 원본은 terminal 상태가 저장된 뒤에 지운다. 삭제가 실패해도 작업 결과는 이미 확정되었으므로
     * 되돌리지 않고, 청소 배치가 다시 시도하도록 imageDeletedAt을 비워 둔다.
     */
    private void deleteOriginal(String publicId, String imageKey) {
        if (storageService.deleteQuietly(imageKey)) {
            processor.markImageDeleted(publicId);
        }
    }
}
