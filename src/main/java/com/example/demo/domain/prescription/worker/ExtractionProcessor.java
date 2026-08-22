package com.example.demo.domain.prescription.worker;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.domain.prescription.analyzer.AnalysisFailedException;
import com.example.demo.domain.prescription.analyzer.PrescriptionAnalyzer;
import com.example.demo.domain.prescription.entity.ExtractionFailureCode;
import com.example.demo.domain.prescription.entity.ExtractionStatus;
import com.example.demo.domain.prescription.entity.PrescriptionExtraction;
import com.example.demo.domain.prescription.repository.PrescriptionExtractionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 작업 하나를 terminal 상태로 확정하는 트랜잭션 단위.
 *
 * <p>원본 이미지 삭제는 여기서 하지 않는다. 문서의 "terminal 상태 저장 후 삭제" 순서를
 * 지키려면 커밋이 끝난 뒤에 지워야 하므로, 그 조율은 {@link ExtractionWorker}가 맡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionProcessor {

    private final PrescriptionExtractionRepository extractionRepository;
    private final PrescriptionAnalyzer analyzer;
    private final Clock clock;

    /**
     * @return 삭제해야 할 원본 이미지 키. 처리할 작업이 없었으면 비어 있다.
     */
    @Transactional
    public Optional<String> analyzeAndFinish(String publicId) {
        PrescriptionExtraction extraction = extractionRepository.findByPublicId(publicId).orElse(null);
        if (extraction == null) {
            log.warn("분석할 작업을 찾지 못했다: id={}", publicId);
            return Optional.empty();
        }
        // 재시도나 스위퍼와 겹칠 수 있다. 이미 끝난 작업은 건드리지 않는다.
        if (extraction.isTerminal()) {
            log.debug("이미 종료된 작업이라 건너뛴다: id={} status={}", publicId, extraction.getStatus());
            return Optional.empty();
        }

        try {
            String resultJson = analyzer.analyze(extraction.getImageKey());
            extraction.complete(resultJson, Instant.now(clock));

        } catch (AnalysisFailedException e) {
            log.warn("분석 실패: id={} code={}", publicId, e.getFailureCode(), e);
            extraction.fail(e.getFailureCode(), Instant.now(clock));

        } catch (RuntimeException e) {
            log.error("분석 중 예상치 못한 오류: id={}", publicId, e);
            extraction.fail(ExtractionFailureCode.INTERNAL_ERROR, Instant.now(clock));
        }

        return Optional.of(extraction.getImageKey());
    }

    @Transactional
    public void markImageDeleted(String publicId) {
        extractionRepository.findByPublicId(publicId)
                .ifPresent(extraction -> extraction.markImageDeleted(Instant.now(clock)));
    }

    /**
     * 기한을 넘긴 pending을 타임아웃 실패로 확정한다.
     *
     * @return 확정된 작업들의 (publicId, imageKey). 원본 삭제는 커밋 이후에 호출자가 한다.
     */
    @Transactional
    public Map<String, String> failStalePending(Duration timeout) {
        Instant now = Instant.now(clock);
        List<PrescriptionExtraction> stale = extractionRepository.findAllByStatusAndCreatedAtBefore(
                ExtractionStatus.PENDING, now.minus(timeout));

        if (stale.isEmpty()) {
            return Map.of();
        }

        log.warn("타임아웃으로 실패 처리하는 작업 {}건", stale.size());
        stale.forEach(extraction -> extraction.fail(ExtractionFailureCode.UPSTREAM_TIMEOUT, now));

        return stale.stream().collect(Collectors.toMap(
                PrescriptionExtraction::getPublicId, PrescriptionExtraction::getImageKey));
    }
}
