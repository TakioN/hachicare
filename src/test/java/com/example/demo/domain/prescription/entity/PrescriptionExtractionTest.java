package com.example.demo.domain.prescription.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class PrescriptionExtractionTest {

    private static final String OWNER = "session-a";
    private static final String IMAGE_KEY = "prescriptions/abc.jpg";
    private static final Instant CREATED_AT = Instant.parse("2026-08-22T10:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-22T10:00:04Z");
    private static final String RESULT_JSON = "{\"reviewStatus\":\"ready\"}";

    private PrescriptionExtraction pendingExtraction() {
        return PrescriptionExtraction.pending(
                PrescriptionExtraction.newPublicId(), OWNER, IMAGE_KEY, CREATED_AT);
    }

    @Test
    void pending_작업은_결과와_실패와_완료시각이_없다() {
        PrescriptionExtraction extraction = pendingExtraction();

        assertThat(extraction.getStatus()).isEqualTo(ExtractionStatus.PENDING);
        assertThat(extraction.getResultJson()).isNull();
        assertThat(extraction.getFailureCode()).isNull();
        assertThat(extraction.getFailureMessage()).isNull();
        assertThat(extraction.getCompletedAt()).isNull();
        assertThat(extraction.isTerminal()).isFalse();
    }

    @Test
    void 공개_식별자는_ext_접두사를_가지며_작업마다_다르다() {
        PrescriptionExtraction first = pendingExtraction();
        PrescriptionExtraction second = pendingExtraction();

        assertThat(first.getPublicId()).startsWith("ext_");
        assertThat(first.getPublicId()).isNotEqualTo(second.getPublicId());
        assertThat(first.getPublicId().length()).isLessThanOrEqualTo(40);
    }

    @Test
    void 완료된_작업은_결과와_완료시각을_가지고_실패정보는_없다() {
        PrescriptionExtraction extraction = pendingExtraction();

        extraction.complete(RESULT_JSON, COMPLETED_AT);

        assertThat(extraction.getStatus()).isEqualTo(ExtractionStatus.COMPLETED);
        assertThat(extraction.getResultJson()).isEqualTo(RESULT_JSON);
        assertThat(extraction.getCompletedAt()).isEqualTo(COMPLETED_AT);
        assertThat(extraction.getFailureCode()).isNull();
        assertThat(extraction.getFailureMessage()).isNull();
        assertThat(extraction.isTerminal()).isTrue();
    }

    @Test
    void 실패한_작업은_실패정보와_완료시각을_가지고_결과는_없다() {
        PrescriptionExtraction extraction = pendingExtraction();

        extraction.fail(ExtractionFailureCode.UPSTREAM_UNAVAILABLE, COMPLETED_AT);

        assertThat(extraction.getStatus()).isEqualTo(ExtractionStatus.FAILED);
        assertThat(extraction.getFailureCode()).isEqualTo(ExtractionFailureCode.UPSTREAM_UNAVAILABLE);
        assertThat(extraction.getFailureMessage()).isEqualTo("처방전을 분석하지 못했습니다.");
        assertThat(extraction.getCompletedAt()).isEqualTo(COMPLETED_AT);
        assertThat(extraction.getResultJson()).isNull();
        assertThat(extraction.isTerminal()).isTrue();
    }

    @Test
    void 이미_완료된_작업은_다시_전이할_수_없다() {
        PrescriptionExtraction extraction = pendingExtraction();
        extraction.complete(RESULT_JSON, COMPLETED_AT);

        assertThatThrownBy(() -> extraction.complete(RESULT_JSON, COMPLETED_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> extraction.fail(ExtractionFailureCode.INTERNAL_ERROR, COMPLETED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 이미_실패한_작업은_다시_전이할_수_없다() {
        PrescriptionExtraction extraction = pendingExtraction();
        extraction.fail(ExtractionFailureCode.UPSTREAM_TIMEOUT, COMPLETED_AT);

        assertThatThrownBy(() -> extraction.complete(RESULT_JSON, COMPLETED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 결과가_비어_있으면_완료_처리할_수_없다() {
        PrescriptionExtraction extraction = pendingExtraction();

        assertThatThrownBy(() -> extraction.complete("  ", COMPLETED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(extraction.getStatus()).isEqualTo(ExtractionStatus.PENDING);
    }

    @Test
    void 소유권은_생성한_세션에만_있다() {
        PrescriptionExtraction extraction = pendingExtraction();

        assertThat(extraction.isOwnedBy(OWNER)).isTrue();
        assertThat(extraction.isOwnedBy("session-b")).isFalse();
    }

    @Test
    void 이미지_삭제_시각은_삭제_후에만_기록된다() {
        PrescriptionExtraction extraction = pendingExtraction();
        assertThat(extraction.getImageDeletedAt()).isNull();

        extraction.markImageDeleted(COMPLETED_AT);

        assertThat(extraction.getImageDeletedAt()).isEqualTo(COMPLETED_AT);
        assertThat(extraction.getImageKey()).isEqualTo(IMAGE_KEY);
    }
}
