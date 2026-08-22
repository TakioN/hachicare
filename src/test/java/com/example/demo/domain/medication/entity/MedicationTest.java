package com.example.demo.domain.medication.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class MedicationTest {

    private static final String OWNER = "usr_abc";
    private static final String EXTRACTION = "ext_abc";
    private static final Instant CREATED_AT = Instant.parse("2026-08-23T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-24T10:00:00Z");

    private Medication registered() {
        return Medication.register(OWNER, EXTRACTION, "아모잘탄정 5/50mg", CREATED_AT);
    }

    @Test
    void 등록하면_복용_중_상태로_시작한다() {
        Medication medication = registered();

        assertThat(medication.getPublicId()).startsWith("med_");
        assertThat(medication.getStatus()).isEqualTo(MedicationStatus.ACTIVE);
        assertThat(medication.getSourceExtractionId()).isEqualTo(EXTRACTION);
        assertThat(medication.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(medication.getUpdatedAt()).isEqualTo(CREATED_AT);
        assertThat(medication.getEndedOn()).isNull();
    }

    @Test
    void 손으로_추가한_약은_출처가_없다() {
        Medication medication = Medication.registerManually(OWNER, "타이레놀", CREATED_AT);

        assertThat(medication.getSourceExtractionId()).isNull();
        assertThat(medication.getStatus()).isEqualTo(MedicationStatus.ACTIVE);
    }

    @Test
    void 약품명은_비울_수_없다() {
        assertThatThrownBy(() -> Medication.register(OWNER, EXTRACTION, "  ", CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Medication.register(OWNER, EXTRACTION, null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 약품명_앞뒤_공백은_지운다() {
        assertThat(Medication.register(OWNER, EXTRACTION, "  타이레놀  ", CREATED_AT).getDrugName())
                .isEqualTo("타이레놀");
    }

    @Test
    void 복용_정보를_채울_수_있다() {
        Medication medication = registered()
                .withDosage(1.0, "정", 3, 30, "아침 식후 30분")
                .withPeriod(LocalDate.of(2026, 8, 23), LocalDate.of(2026, 9, 21));

        assertThat(medication.getDoseValue()).isEqualTo(1.0);
        assertThat(medication.getDoseUnit()).isEqualTo("정");
        assertThat(medication.getFrequencyPerDay()).isEqualTo(3);
        assertThat(medication.getDurationDays()).isEqualTo(30);
        assertThat(medication.getTimingInstruction()).isEqualTo("아침 식후 30분");
        assertThat(medication.getStartedOn()).isEqualTo(LocalDate.of(2026, 8, 23));
    }

    @Test
    void 수정하면_수정시각이_바뀐다() {
        Medication medication = registered();

        medication.update("타이레놀", 2.0, "정", 2, 7, "취침 전", null, null, UPDATED_AT);

        assertThat(medication.getDrugName()).isEqualTo("타이레놀");
        assertThat(medication.getFrequencyPerDay()).isEqualTo(2);
        assertThat(medication.getUpdatedAt()).isEqualTo(UPDATED_AT);
        assertThat(medication.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void 종료일이_시작일보다_앞설_수_없다() {
        Medication medication = registered();

        assertThatThrownBy(() -> medication.withPeriod(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> medication.update("타이레놀", null, null, null, null, null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1), UPDATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 복용을_끝내도_기록은_남는다() {
        Medication medication = registered().withPeriod(LocalDate.of(2026, 8, 23), null);

        medication.finish(LocalDate.of(2026, 9, 1), UPDATED_AT);

        assertThat(medication.getStatus()).isEqualTo(MedicationStatus.FINISHED);
        assertThat(medication.getEndedOn()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(medication.getDrugName()).isNotBlank();
    }

    @Test
    void 다시_복용을_시작하면_종료일이_지워진다() {
        Medication medication = registered();
        medication.finish(LocalDate.of(2026, 9, 1), UPDATED_AT);

        medication.resume(UPDATED_AT);

        assertThat(medication.getStatus()).isEqualTo(MedicationStatus.ACTIVE);
        assertThat(medication.getEndedOn()).isNull();
    }

    @Test
    void 시작일이_없으면_아무_종료일이나_받는다() {
        Medication medication = registered();

        assertThatCode(() -> medication.finish(LocalDate.of(2020, 1, 1), UPDATED_AT))
                .doesNotThrowAnyException();
    }

    @Test
    void 소유권은_등록한_사용자에게만_있다() {
        Medication medication = registered();

        assertThat(medication.isOwnedBy(OWNER)).isTrue();
        assertThat(medication.isOwnedBy("usr_other")).isFalse();
    }
}
