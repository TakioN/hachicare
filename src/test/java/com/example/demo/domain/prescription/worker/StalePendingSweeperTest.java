package com.example.demo.domain.prescription.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.domain.prescription.entity.ExtractionFailureCode;
import com.example.demo.domain.prescription.entity.ExtractionStatus;
import com.example.demo.domain.prescription.entity.PrescriptionExtraction;
import com.example.demo.domain.prescription.repository.PrescriptionExtractionRepository;
import com.example.demo.global.storage.StorageService;

@SpringBootTest
@Transactional
class StalePendingSweeperTest {

    @Autowired
    private StalePendingSweeper sweeper;

    @Autowired
    private PrescriptionExtractionRepository extractionRepository;

    @MockitoBean
    private StorageService storageService;

    @BeforeEach
    void stubDelete() {
        given(storageService.deleteQuietly(anyString())).willReturn(true);
    }

    private PrescriptionExtraction savePending(String imageKey, Instant createdAt) {
        return extractionRepository.save(PrescriptionExtraction.pending(
                PrescriptionExtraction.newPublicId(), "session-a", imageKey, createdAt));
    }

    @Test
    void 기한을_넘긴_pending은_타임아웃_실패로_확정된다() {
        // 기본 타임아웃 2분보다 훨씬 오래된 작업
        PrescriptionExtraction stale = savePending(
                "prescriptions/stale.png", Instant.now().minus(30, ChronoUnit.MINUTES));

        sweeper.sweep();

        PrescriptionExtraction swept =
                extractionRepository.findByPublicId(stale.getPublicId()).orElseThrow();
        assertThat(swept.getStatus()).isEqualTo(ExtractionStatus.FAILED);
        assertThat(swept.getFailureCode()).isEqualTo(ExtractionFailureCode.UPSTREAM_TIMEOUT);
        assertThat(swept.getCompletedAt()).isNotNull();
        assertThat(swept.getImageDeletedAt()).isNotNull();
    }

    @Test
    void 아직_기한_안인_pending은_건드리지_않는다() {
        PrescriptionExtraction fresh = savePending("prescriptions/fresh.png", Instant.now());

        sweeper.sweep();

        PrescriptionExtraction untouched =
                extractionRepository.findByPublicId(fresh.getPublicId()).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(ExtractionStatus.PENDING);
        assertThat(untouched.getCompletedAt()).isNull();
    }

    @Test
    void 이미_끝난_작업은_스위퍼가_다시_건드리지_않는다() {
        PrescriptionExtraction done = savePending(
                "prescriptions/done.png", Instant.now().minus(30, ChronoUnit.MINUTES));
        Instant completedAt = Instant.parse("2026-08-22T10:00:04Z");
        done.complete("{\"reviewStatus\":\"ready\"}", completedAt);

        sweeper.sweep();

        PrescriptionExtraction untouched =
                extractionRepository.findByPublicId(done.getPublicId()).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(ExtractionStatus.COMPLETED);
        assertThat(untouched.getCompletedAt()).isEqualTo(completedAt);
    }
}
