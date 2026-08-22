package com.example.demo.domain.prescription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.demo.domain.prescription.repository.PrescriptionExtractionRepository;
import com.example.demo.global.storage.StorageService;
import com.example.demo.support.TestImages;

/**
 * 스토리지에 먼저 쓰고 DB에 나중에 쓰는 순서 때문에 생기는 고아 오브젝트를,
 * 롤백 보상 삭제가 실제로 회수하는지 확인한다.
 *
 * <p>테스트에 @Transactional을 붙이지 않는다. 붙이면 서비스가 테스트 트랜잭션에 참여해
 * afterCompletion이 테스트 종료 시점까지 밀려 검증할 수 없다.
 */
@SpringBootTest
class PrescriptionExtractionRollbackTest {

    @Autowired
    private PrescriptionExtractionService extractionService;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private PrescriptionExtractionRepository extractionRepository;

    @BeforeEach
    void stubUpload() {
        given(storageService.upload(any(), anyString()))
                .willAnswer(invocation -> "prescriptions/" + invocation.getArgument(1) + ".png");
    }

    private MockMultipartFile validDocument() {
        return new MockMultipartFile("document", "prescription.png", "image/png", TestImages.png());
    }

    @Test
    void DB_저장이_실패하면_업로드한_원본을_지운다() {
        given(extractionRepository.save(any())).willThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> extractionService.create(validDocument(), "session-a"))
                .isInstanceOf(DataIntegrityViolationException.class);

        then(storageService).should().deleteQuietly(anyString());
    }

    @Test
    void 정상_생성이면_업로드한_원본을_지우지_않는다() {
        given(extractionRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        extractionService.create(validDocument(), "session-a");

        then(storageService).should(org.mockito.Mockito.never()).deleteQuietly(anyString());
    }

    @Test
    void 지우는_대상은_방금_업로드한_그_키다() {
        given(extractionRepository.save(any())).willThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> extractionService.create(validDocument(), "session-a"))
                .isInstanceOf(DataIntegrityViolationException.class);

        org.mockito.ArgumentCaptor<String> uploadedName = org.mockito.ArgumentCaptor.forClass(String.class);
        then(storageService).should().upload(any(), uploadedName.capture());

        org.mockito.ArgumentCaptor<String> deletedKey = org.mockito.ArgumentCaptor.forClass(String.class);
        then(storageService).should().deleteQuietly(deletedKey.capture());

        assertThat(deletedKey.getValue()).isEqualTo("prescriptions/" + uploadedName.getValue() + ".png");
    }
}
