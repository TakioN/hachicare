package com.example.demo.domain.prescription.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.domain.prescription.entity.ExtractionStatus;
import com.example.demo.domain.prescription.entity.PrescriptionExtraction;

public interface PrescriptionExtractionRepository extends JpaRepository<PrescriptionExtraction, Long> {

    Optional<PrescriptionExtraction> findByPublicId(String publicId);

    /** 워커가 죽어 pending에 머무른 작업 회수용. */
    List<PrescriptionExtraction> findAllByStatusAndCreatedAtBefore(ExtractionStatus status, Instant threshold);

    /** terminal 상태인데 원본 이미지가 아직 남아 있는 작업 정리용. */
    List<PrescriptionExtraction> findTop100ByStatusNotAndImageDeletedAtIsNullOrderByCreatedAtAsc(
            ExtractionStatus status);
}
