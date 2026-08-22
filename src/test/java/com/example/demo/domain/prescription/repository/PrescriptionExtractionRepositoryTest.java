package com.example.demo.domain.prescription.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.example.demo.domain.prescription.entity.ExtractionFailureCode;
import com.example.demo.domain.prescription.entity.ExtractionStatus;
import com.example.demo.domain.prescription.entity.PrescriptionExtraction;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PrescriptionExtractionRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");

    @Autowired
    private PrescriptionExtractionRepository repository;

    private PrescriptionExtraction save(String imageKey, Instant createdAt) {
        return repository.save(PrescriptionExtraction.pending(
                PrescriptionExtraction.newPublicId(), "session-a", imageKey, createdAt));
    }

    @Test
    void 공개_식별자로_조회한다() {
        PrescriptionExtraction saved = save("prescriptions/a.jpg", NOW);
        repository.flush();

        assertThat(repository.findByPublicId(saved.getPublicId()))
                .get()
                .extracting(PrescriptionExtraction::getImageKey, PrescriptionExtraction::getStatus)
                .containsExactly("prescriptions/a.jpg", ExtractionStatus.PENDING);
        assertThat(repository.findByPublicId("ext_없는값")).isEmpty();
    }

    @Test
    void 기준_시각보다_오래된_pending_작업만_회수한다() {
        PrescriptionExtraction stale = save("prescriptions/stale.jpg", NOW.minus(10, ChronoUnit.MINUTES));
        PrescriptionExtraction fresh = save("prescriptions/fresh.jpg", NOW);
        repository.flush();

        List<PrescriptionExtraction> found = repository.findAllByStatusAndCreatedAtBefore(
                ExtractionStatus.PENDING, NOW.minus(5, ChronoUnit.MINUTES));

        assertThat(found).extracting(PrescriptionExtraction::getPublicId)
                .contains(stale.getPublicId())
                .doesNotContain(fresh.getPublicId());
    }

    @Test
    void 이미지가_남아_있는_종료된_작업만_정리_대상이다() {
        PrescriptionExtraction completed = save("prescriptions/done.jpg", NOW);
        completed.complete("{\"reviewStatus\":\"ready\"}", NOW);

        PrescriptionExtraction failed = save("prescriptions/failed.jpg", NOW);
        failed.fail(ExtractionFailureCode.UPSTREAM_TIMEOUT, NOW);

        PrescriptionExtraction cleaned = save("prescriptions/cleaned.jpg", NOW);
        cleaned.complete("{\"reviewStatus\":\"ready\"}", NOW);
        cleaned.markImageDeleted(NOW);

        PrescriptionExtraction stillPending = save("prescriptions/pending.jpg", NOW);
        repository.flush();

        List<PrescriptionExtraction> found =
                repository.findTop100ByStatusNotAndImageDeletedAtIsNullOrderByCreatedAtAsc(
                        ExtractionStatus.PENDING);

        assertThat(found).extracting(PrescriptionExtraction::getPublicId)
                .contains(completed.getPublicId(), failed.getPublicId())
                .doesNotContain(cleaned.getPublicId(), stillPending.getPublicId());
    }
}
