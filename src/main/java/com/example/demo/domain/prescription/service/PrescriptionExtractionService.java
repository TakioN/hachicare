package com.example.demo.domain.prescription.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.domain.prescription.dto.ExtractionJobResponse;
import com.example.demo.domain.prescription.entity.PrescriptionExtraction;
import com.example.demo.domain.prescription.repository.PrescriptionExtractionRepository;
import com.example.demo.global.exception.CustomException;
import com.example.demo.global.exception.ErrorCode;
import com.example.demo.global.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionExtractionService {

    private final PrescriptionExtractionRepository extractionRepository;
    private final PrescriptionImageValidator imageValidator;
    private final ExtractionJobMapper jobMapper;
    private final StorageService storageService;
    private final Clock clock;

    /**
     * 조회는 작업을 만든 세션에만 허용한다.
     * 없는 작업은 404, 남의 작업은 403으로 구분한다.
     */
    @Transactional(readOnly = true)
    public ExtractionJobResponse find(String publicId, String ownerKey) {
        PrescriptionExtraction extraction = extractionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new CustomException(ErrorCode.EXTRACTION_NOT_FOUND));

        if (ownerKey == null || !extraction.isOwnedBy(ownerKey)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return jobMapper.toResponse(extraction);
    }

    /**
     * 동기 구간만 처리한다. 검증 → 원본 업로드 → pending 작업 생성.
     * 실제 분석은 6단계에서 커밋 이후 워커로 넘긴다.
     */
    @Transactional
    public ExtractionJobResponse create(MultipartFile document, String ownerKey) {
        imageValidator.validate(document);

        // 오브젝트 키를 작업 식별자로 짓기 위해 저장 전에 발급한다.
        String publicId = PrescriptionExtraction.newPublicId();
        String imageKey = storageService.upload(document, publicId);
        deleteUploadedImageIfRolledBack(imageKey);

        PrescriptionExtraction extraction = extractionRepository.save(
                PrescriptionExtraction.pending(publicId, ownerKey, imageKey, Instant.now(clock)));

        return jobMapper.toResponse(extraction);
    }

    /**
     * 스토리지에 먼저 쓰고 DB에 나중에 쓰므로, 그 사이에 실패하면 오브젝트만 남는다.
     * 롤백 시 되돌려 고아를 없앤다. 프로세스가 그 사이에 죽는 경우까지는 못 막으므로
     * 버킷 라이프사이클 정책을 안전망으로 함께 둘 것.
     */
    private void deleteUploadedImageIfRolledBack(String imageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    log.warn("작업 생성이 롤백되어 업로드한 원본을 삭제한다: key={}", imageKey);
                    storageService.deleteQuietly(imageKey);
                }
            }
        });
    }
}
