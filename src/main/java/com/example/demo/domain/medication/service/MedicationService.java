package com.example.demo.domain.medication.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.domain.medication.dto.MedicationBulkCreateRequest;
import com.example.demo.domain.medication.dto.MedicationRequest;
import com.example.demo.domain.medication.dto.MedicationResponse;
import com.example.demo.domain.medication.entity.Medication;
import com.example.demo.domain.medication.entity.MedicationStatus;
import com.example.demo.domain.medication.repository.MedicationRepository;
import com.example.demo.global.exception.CustomException;
import com.example.demo.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationService {

    private final MedicationRepository medicationRepository;
    private final Clock clock;

    /**
     * 목록을 한 번에 등록한다. 하나라도 잘못되면 전부 등록하지 않는다.
     *
     * <p>약 개수만큼 나눠 부르면 중간에 실패했을 때 절반만 남는다. 사용자가 확인 화면에서
     * 한 번 누른 행위이므로 저장도 한 번에 끝나야 한다.
     */
    @Transactional
    public List<MedicationResponse> createAll(MedicationBulkCreateRequest request, String ownerKey) {
        List<MedicationRequest> items = request.medications();
        if (items == null || items.isEmpty()) {
            throw new CustomException(ErrorCode.EMPTY_MEDICATIONS);
        }

        String sourceExtractionId = request.sourceExtractionId();
        // 확인 화면에서 저장을 두 번 누르면 같은 약이 두 벌 생긴다.
        if (sourceExtractionId != null
                && medicationRepository.existsByOwnerKeyAndSourceExtractionId(
                        ownerKey, sourceExtractionId)) {
            throw new CustomException(ErrorCode.EXTRACTION_ALREADY_CONFIRMED);
        }

        Instant now = Instant.now(clock);
        List<Medication> saved = medicationRepository.saveAll(
                guarded(() -> items.stream()
                        .map(item -> toEntity(item, sourceExtractionId, ownerKey, now))
                        .toList()));

        return saved.stream().map(MedicationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MedicationResponse> findAll(String ownerKey, MedicationStatus status) {
        List<Medication> found = status == null
                ? medicationRepository.findAllByOwnerKeyOrderByCreatedAtDesc(ownerKey)
                : medicationRepository.findAllByOwnerKeyAndStatusOrderByCreatedAtDesc(ownerKey, status);

        return found.stream().map(MedicationResponse::from).toList();
    }

    @Transactional
    public MedicationResponse update(String publicId, MedicationRequest request, String ownerKey) {
        Medication medication = findOwned(publicId, ownerKey);

        guarded(() -> {
            medication.update(
                    request.drugName(), request.doseValue(), request.doseUnit(),
                    request.frequencyPerDay(), request.durationDays(), request.timingInstruction(),
                    request.startedOn(), request.endedOn(), Instant.now(clock));
            return null;
        });

        return MedicationResponse.from(medication);
    }

    /** 복용 종료. 지우지 않고 상태만 바꿔 이력을 남긴다. */
    @Transactional
    public MedicationResponse finish(String publicId, LocalDate endedOn, String ownerKey) {
        Medication medication = findOwned(publicId, ownerKey);
        guarded(() -> {
            medication.finish(
                    Optional.ofNullable(endedOn).orElseGet(() -> LocalDate.now(clock)),
                    Instant.now(clock));
            return null;
        });

        return MedicationResponse.from(medication);
    }

    /** 잘못 등록한 것을 없앤다. 복용을 끝낸 경우에는 finish를 쓴다. */
    @Transactional
    public void delete(String publicId, String ownerKey) {
        medicationRepository.delete(findOwned(publicId, ownerKey));
    }

    /**
     * 도메인 규칙 위반은 사용자 입력 문제다. 그대로 두면 500으로 나가 원인을 알 수 없다.
     * 상세 사유는 로그에만 남기고 응답에는 일반 문구를 준다.
     */
    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            log.warn("약 정보가 유효하지 않음: {}", e.getMessage());
            throw new CustomException(ErrorCode.INVALID_MEDICATION);
        }
    }

    private Medication findOwned(String publicId, String ownerKey) {
        Medication medication = medicationRepository.findByPublicId(publicId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEDICATION_NOT_FOUND));

        if (!medication.isOwnedBy(ownerKey)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        return medication;
    }

    private static Medication toEntity(
            MedicationRequest request, String sourceExtractionId, String ownerKey, Instant now) {

        return Medication.register(ownerKey, sourceExtractionId, request.drugName(), now)
                .withDosage(
                        request.doseValue(), request.doseUnit(), request.frequencyPerDay(),
                        request.durationDays(), request.timingInstruction())
                .withPeriod(request.startedOn(), request.endedOn());
    }
}
