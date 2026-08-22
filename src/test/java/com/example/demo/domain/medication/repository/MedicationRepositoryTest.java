package com.example.demo.domain.medication.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.example.demo.domain.medication.entity.Medication;
import com.example.demo.domain.medication.entity.MedicationStatus;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MedicationRepositoryTest {

    private static final String OWNER = "usr_owner";
    private static final String OTHER = "usr_other";
    private static final Instant NOW = Instant.parse("2026-08-23T10:00:00Z");

    @Autowired
    private MedicationRepository repository;

    private Medication save(String ownerKey, String drugName, Instant createdAt) {
        return repository.save(Medication.register(ownerKey, "ext_abc", drugName, createdAt));
    }

    @Test
    void 공개_식별자로_조회한다() {
        Medication saved = save(OWNER, "아모잘탄정", NOW);
        repository.flush();

        assertThat(repository.findByPublicId(saved.getPublicId()))
                .get()
                .extracting(Medication::getDrugName, Medication::getStatus)
                .containsExactly("아모잘탄정", MedicationStatus.ACTIVE);
        assertThat(repository.findByPublicId("med_없는값")).isEmpty();
    }

    @Test
    void 사용자별로_최신순으로_가져온다() {
        Medication older = save(OWNER, "오래된약", NOW.minusSeconds(60));
        Medication newer = save(OWNER, "최근약", NOW);
        Medication someoneElse = save(OTHER, "남의약", NOW);
        repository.flush();

        List<Medication> found = repository.findAllByOwnerKeyOrderByCreatedAtDesc(OWNER);

        assertThat(found).extracting(Medication::getPublicId)
                .containsExactly(newer.getPublicId(), older.getPublicId())
                .doesNotContain(someoneElse.getPublicId());
    }

    @Test
    void 복용_중인_것만_추릴_수_있다() {
        Medication active = save(OWNER, "복용중", NOW);
        Medication finished = save(OWNER, "복용끝", NOW);
        finished.finish(null, NOW);
        repository.flush();

        assertThat(repository.findAllByOwnerKeyAndStatusOrderByCreatedAtDesc(
                        OWNER, MedicationStatus.ACTIVE))
                .extracting(Medication::getPublicId)
                .contains(active.getPublicId())
                .doesNotContain(finished.getPublicId());
    }

    @Test
    void 같은_분석에서_이미_등록했는지_알_수_있다() {
        save(OWNER, "아모잘탄정", NOW);
        repository.flush();

        // 확정을 두 번 눌러도 중복 등록하지 않기 위한 확인
        assertThat(repository.existsByOwnerKeyAndSourceExtractionId(OWNER, "ext_abc")).isTrue();
        assertThat(repository.existsByOwnerKeyAndSourceExtractionId(OWNER, "ext_other")).isFalse();
        assertThat(repository.existsByOwnerKeyAndSourceExtractionId(OTHER, "ext_abc")).isFalse();
    }
}
