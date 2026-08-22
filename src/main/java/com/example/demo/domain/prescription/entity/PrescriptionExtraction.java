package com.example.demo.domain.prescription.entity;

import java.time.Instant;
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
 * 처방전 분석 작업.
 *
 * <p>상태 불변조건은 이 클래스의 전이 메서드로만 지켜진다. 필드를 직접 바꾸지 말 것.
 * <ul>
 *   <li>{@code PENDING}: result, failure, completedAt 없음
 *   <li>{@code COMPLETED}: result, completedAt 필수
 *   <li>{@code FAILED}: failure, completedAt 필수
 *   <li>result와 failure는 동시에 존재하지 않음
 * </ul>
 */
@Entity
@Table(
    name = "prescription_extraction",
    indexes = {
        @Index(name = "ux_extraction_public_id", columnList = "public_id", unique = true),
        @Index(name = "ix_extraction_status_created_at", columnList = "status, created_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrescriptionExtraction {

    private static final String PUBLIC_ID_PREFIX = "ext_";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 외부에 노출하는 식별자. 내부 PK를 그대로 노출하지 않는다. */
    @Column(nullable = false, unique = true, updatable = false, length = 40)
    private String publicId;

    /** 작업을 생성한 익명 세션. 조회 시 소유권 판단 기준. */
    @Column(nullable = false, updatable = false, length = 64)
    private String ownerKey;

    /** 오브젝트 스토리지 키. 삭제 후에도 추적을 위해 값은 남긴다. */
    @Column(nullable = false, updatable = false, length = 512)
    private String imageKey;

    /** 원본 이미지를 실제로 지운 시각. null이면 아직 남아 있다는 뜻. */
    private Instant imageDeletedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExtractionStatus status;

    /** PrescriptionExtractionResultDto의 JSON 표현. 스키마 변동이 잦아 컬럼으로 펼치지 않는다. */
    @Column(columnDefinition = "TEXT")
    private String resultJson;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private ExtractionFailureCode failureCode;

    @Column(length = 500)
    private String failureMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant completedAt;

    private PrescriptionExtraction(String publicId, String ownerKey, String imageKey, Instant createdAt) {
        this.publicId = publicId;
        this.ownerKey = ownerKey;
        this.imageKey = imageKey;
        this.status = ExtractionStatus.PENDING;
        this.createdAt = createdAt;
    }

    /**
     * 오브젝트 키를 이 값으로 짓기 위해 저장 전에 미리 발급받을 수 있어야 한다.
     * 그래야 스토리지에 남은 파일만 보고도 어느 작업 것인지 판별할 수 있다.
     */
    public static String newPublicId() {
        return PUBLIC_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    public static PrescriptionExtraction pending(
            String publicId, String ownerKey, String imageKey, Instant createdAt) {
        return new PrescriptionExtraction(publicId, ownerKey, imageKey, createdAt);
    }

    public void complete(String resultJson, Instant completedAt) {
        requirePending();
        if (resultJson == null || resultJson.isBlank()) {
            throw new IllegalArgumentException("완료 처리에는 분석 결과가 필요합니다: " + publicId);
        }
        this.status = ExtractionStatus.COMPLETED;
        this.resultJson = resultJson;
        this.failureCode = null;
        this.failureMessage = null;
        this.completedAt = completedAt;
    }

    public void fail(ExtractionFailureCode failureCode, Instant completedAt) {
        requirePending();
        if (failureCode == null) {
            throw new IllegalArgumentException("실패 처리에는 실패 코드가 필요합니다: " + publicId);
        }
        this.status = ExtractionStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureCode.getDefaultMessage();
        this.resultJson = null;
        this.completedAt = completedAt;
    }

    public void markImageDeleted(Instant deletedAt) {
        this.imageDeletedAt = deletedAt;
    }

    public boolean isTerminal() {
        return status != ExtractionStatus.PENDING;
    }

    public boolean isOwnedBy(String ownerKey) {
        return this.ownerKey.equals(ownerKey);
    }

    private void requirePending() {
        if (isTerminal()) {
            throw new IllegalStateException(
                    "이미 종료된 작업은 다시 전이할 수 없습니다: " + publicId + " (" + status + ")");
        }
    }
}
