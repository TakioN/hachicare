package com.example.demo.domain.medication.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.domain.medication.entity.Medication;
import com.example.demo.domain.medication.entity.MedicationStatus;

public interface MedicationRepository extends JpaRepository<Medication, Long> {

    Optional<Medication> findByPublicId(String publicId);

    List<Medication> findAllByOwnerKeyOrderByCreatedAtDesc(String ownerKey);

    List<Medication> findAllByOwnerKeyAndStatusOrderByCreatedAtDesc(
            String ownerKey, MedicationStatus status);

    /** 같은 분석에서 이미 등록했는지 확인할 때. 확정을 두 번 눌러도 중복되지 않게 한다. */
    boolean existsByOwnerKeyAndSourceExtractionId(String ownerKey, String sourceExtractionId);
}
