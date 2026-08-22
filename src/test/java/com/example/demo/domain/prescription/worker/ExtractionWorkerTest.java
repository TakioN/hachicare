package com.example.demo.domain.prescription.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.domain.prescription.analyzer.AnalysisFailedException;
import com.example.demo.domain.prescription.analyzer.PrescriptionAnalyzer;
import com.example.demo.domain.prescription.entity.ExtractionFailureCode;
import com.example.demo.domain.prescription.entity.ExtractionStatus;
import com.example.demo.domain.prescription.entity.PrescriptionExtraction;
import com.example.demo.domain.prescription.repository.PrescriptionExtractionRepository;
import com.example.demo.domain.prescription.dto.result.DocumentType;
import com.example.demo.domain.prescription.dto.result.PrescriptionExtractionResult;
import com.example.demo.global.storage.StorageService;
import com.example.demo.support.TestResults;

@SpringBootTest
@Transactional
class ExtractionWorkerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-22T10:00:00Z");
    private static final String IMAGE_KEY = "prescriptions/ext_x.png";
    private static final PrescriptionExtractionResult RESULT = TestResults.valid();

    @Autowired
    private ExtractionWorker worker;

    @Autowired
    private PrescriptionExtractionRepository extractionRepository;

    @MockitoBean
    private PrescriptionAnalyzer analyzer;

    @MockitoBean
    private StorageService storageService;

    private PrescriptionExtraction savePending() {
        return extractionRepository.save(PrescriptionExtraction.pending(
                PrescriptionExtraction.newPublicId(), "session-a", IMAGE_KEY, CREATED_AT));
    }

    @Test
    void 분석에_성공하면_completed로_확정하고_원본을_지운다() {
        PrescriptionExtraction extraction = savePending();
        given(analyzer.analyze(IMAGE_KEY)).willReturn(RESULT);
        given(storageService.deleteQuietly(IMAGE_KEY)).willReturn(true);

        worker.process(extraction.getPublicId());

        PrescriptionExtraction finished =
                extractionRepository.findByPublicId(extraction.getPublicId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(ExtractionStatus.COMPLETED);
        assertThat(finished.getResultJson())
                .contains("\"reviewStatus\":\"ready\"")
                .contains("\"documentType\":\"patient_copy_prescription\"")
                .contains("\"id\":\"med_1\"");
        assertThat(finished.getCompletedAt()).isNotNull();
        assertThat(finished.getImageDeletedAt()).isNotNull();
        then(storageService).should().deleteQuietly(IMAGE_KEY);
    }

    @Test
    void 분석이_실패하면_그_실패코드로_failed가_된다() {
        PrescriptionExtraction extraction = savePending();
        given(analyzer.analyze(IMAGE_KEY)).willThrow(
                new AnalysisFailedException(ExtractionFailureCode.UPSTREAM_UNAVAILABLE, "503"));
        given(storageService.deleteQuietly(IMAGE_KEY)).willReturn(true);

        worker.process(extraction.getPublicId());

        PrescriptionExtraction finished =
                extractionRepository.findByPublicId(extraction.getPublicId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(ExtractionStatus.FAILED);
        assertThat(finished.getFailureCode()).isEqualTo(ExtractionFailureCode.UPSTREAM_UNAVAILABLE);
        assertThat(finished.getResultJson()).isNull();
    }

    @Test
    void 계약을_어긴_분석_결과는_invalid_agent_response로_확정된다() {
        PrescriptionExtraction extraction = savePending();
        // reviewStatus가 없는 결과. 그대로 저장하면 클라이언트가 렌더링에 실패한다.
        given(analyzer.analyze(IMAGE_KEY)).willReturn(new PrescriptionExtractionResult(
                null, DocumentType.OTHER, List.of(), List.of()));
        given(storageService.deleteQuietly(IMAGE_KEY)).willReturn(true);

        worker.process(extraction.getPublicId());

        PrescriptionExtraction finished =
                extractionRepository.findByPublicId(extraction.getPublicId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(ExtractionStatus.FAILED);
        assertThat(finished.getFailureCode())
                .isEqualTo(ExtractionFailureCode.INVALID_AGENT_RESPONSE);
        assertThat(finished.getResultJson()).isNull();
    }

    @Test
    void 예상치_못한_오류는_internal_error로_확정된다() {
        PrescriptionExtraction extraction = savePending();
        given(analyzer.analyze(IMAGE_KEY)).willThrow(new IllegalStateException("boom"));
        given(storageService.deleteQuietly(IMAGE_KEY)).willReturn(true);

        worker.process(extraction.getPublicId());

        PrescriptionExtraction finished =
                extractionRepository.findByPublicId(extraction.getPublicId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(ExtractionStatus.FAILED);
        assertThat(finished.getFailureCode()).isEqualTo(ExtractionFailureCode.INTERNAL_ERROR);
    }

    @Test
    void 원본_삭제가_실패하면_삭제된_것으로_표시하지_않는다() {
        PrescriptionExtraction extraction = savePending();
        given(analyzer.analyze(IMAGE_KEY)).willReturn(RESULT);
        given(storageService.deleteQuietly(IMAGE_KEY)).willReturn(false);

        worker.process(extraction.getPublicId());

        PrescriptionExtraction finished =
                extractionRepository.findByPublicId(extraction.getPublicId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(ExtractionStatus.COMPLETED);
        assertThat(finished.getImageDeletedAt()).isNull();
    }

    @Test
    void 이미_끝난_작업은_다시_분석하지_않는다() {
        PrescriptionExtraction extraction = savePending();
        extraction.complete("{\"reviewStatus\":\"ready\"}", CREATED_AT);

        worker.process(extraction.getPublicId());

        then(analyzer).should(never()).analyze(anyString());
        then(storageService).should(never()).deleteQuietly(anyString());
    }

    @Test
    void 없는_작업이면_조용히_넘어간다() {
        worker.process("ext_doesnotexist");

        then(analyzer).should(never()).analyze(anyString());
        then(storageService).should(never()).deleteQuietly(anyString());
    }
}
