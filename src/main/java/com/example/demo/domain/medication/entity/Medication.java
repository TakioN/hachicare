package com.example.demo.domain.medication.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 복용 중인 약. 사용자가 직접 고치고 지우는 가변 기록이다.
 *
 * <p>처방전 분석 결과({@code prescription_extraction.result_json})와는 별개다.
 * 그쪽은 "그때 처방전에 이렇게 적혀 있었다"는 불변 스냅샷이고, 이쪽은 "지금 이 약을 먹는다"는
 * 현재 상태다. 사용자가 약을 지워도 분석 기록은 그대로 남아야 한다.
 *
 * <p>분석에서 확정해 넘어온 약은 {@code sourceExtractionId}로 출처를 가리키고,
 * 손으로 추가한 약은 그 값이 비어 있다.
 */
@Entity
@Table(
    name = "medication",
    indexes = {
        @Index(name = "ux_medication_public_id", columnList = "public_id", unique = true),
        @Index(name = "ix_medication_owner_status", columnList = "owner_key, status")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Medication {

    private static final String PUBLIC_ID_PREFIX = "med_";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 40)
    private String publicId;

    /** 소유자의 공개 식별자. 처방전 작업의 ownerKey와 같은 값이다. */
    @Column(nullable = false, updatable = false, length = 64)
    private String ownerKey;

    /** 이 약이 나온 분석 작업. 손으로 추가했으면 비어 있다. */
    @Column(updatable = false, length = 40)
    private String sourceExtractionId;

    @Column(nullable = false, length = 200)
    private String drugName;

    /** 1회 투여량. 사용자가 고칠 수 있으므로 원문은 여기 두지 않는다. */
    private Double doseValue;

    @Column(length = 20)
    private String doseUnit;

    private Integer frequencyPerDay;

    private Integer durationDays;

    @Column(length = 200)
    private String timingInstruction;

    private LocalDate startedOn;

    private LocalDate endedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MedicationStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Medication(String ownerKey, String sourceExtractionId, String drugName, Instant now) {
        this.publicId = PUBLIC_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
        this.ownerKey = ownerKey;
        this.sourceExtractionId = sourceExtractionId;
        this.drugName = requireDrugName(drugName);
        this.status = MedicationStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Medication register(
            String ownerKey, String sourceExtractionId, String drugName, Instant now) {
        return new Medication(ownerKey, sourceExtractionId, drugName, now);
    }

    /** 손으로 추가한 약. 출처가 되는 분석 작업이 없다. */
    public static Medication registerManually(String ownerKey, String drugName, Instant now) {
        return new Medication(ownerKey, null, drugName, now);
    }

    /** 복용 정보를 한 번에 채운다. 값이 없는 항목은 null로 두면 된다. */
    public Medication withDosage(
            Double doseValue, String doseUnit, Integer frequencyPerDay,
            Integer durationDays, String timingInstruction) {

        this.doseValue = doseValue;
        this.doseUnit = doseUnit;
        this.frequencyPerDay = frequencyPerDay;
        this.durationDays = durationDays;
        this.timingInstruction = timingInstruction;
        return this;
    }

    public Medication withPeriod(LocalDate startedOn, LocalDate endedOn) {
        requireValidPeriod(startedOn, endedOn);
        this.startedOn = startedOn;
        this.endedOn = endedOn;
        return this;
    }

    public void update(
            String drugName, Double doseValue, String doseUnit, Integer frequencyPerDay,
            Integer durationDays, String timingInstruction,
            LocalDate startedOn, LocalDate endedOn, Instant now) {

        requireValidPeriod(startedOn, endedOn);
        this.drugName = requireDrugName(drugName);
        this.doseValue = doseValue;
        this.doseUnit = doseUnit;
        this.frequencyPerDay = frequencyPerDay;
        this.durationDays = durationDays;
        this.timingInstruction = timingInstruction;
        this.startedOn = startedOn;
        this.endedOn = endedOn;
        this.updatedAt = now;
    }

    /** 복용을 끝낸다. 지우지 않고 상태만 바꿔 기록을 남긴다. */
    public void finish(LocalDate endedOn, Instant now) {
        requireValidPeriod(this.startedOn, endedOn);
        this.status = MedicationStatus.FINISHED;
        this.endedOn = endedOn;
        this.updatedAt = now;
    }

    public void resume(Instant now) {
        this.status = MedicationStatus.ACTIVE;
        this.endedOn = null;
        this.updatedAt = now;
    }

    public boolean isOwnedBy(String ownerKey) {
        return this.ownerKey.equals(ownerKey);
    }

    private static String requireDrugName(String drugName) {
        if (drugName == null || drugName.isBlank()) {
            throw new IllegalArgumentException("약품명은 비울 수 없습니다.");
        }
        return drugName.trim();
    }

    private static void requireValidPeriod(LocalDate startedOn, LocalDate endedOn) {
        if (startedOn != null && endedOn != null && endedOn.isBefore(startedOn)) {
            throw new IllegalArgumentException(
                    "복용 종료일이 시작일보다 앞설 수 없습니다: " + startedOn + " ~ " + endedOn);
        }
    }
}
