package com.example.demo.domain.prescription.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.domain.prescription.entity.ExtractionStatus;
import com.example.demo.domain.prescription.event.ExtractionRequestedEvent;
import com.example.demo.domain.prescription.entity.PrescriptionExtraction;
import com.example.demo.domain.prescription.repository.PrescriptionExtractionRepository;
import com.example.demo.global.storage.StorageService;
import com.example.demo.support.TestImages;
import com.example.demo.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class PrescriptionExtractionControllerTest {

    private static final String ENDPOINT = "/api/v1/prescription-extractions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrescriptionExtractionRepository extractionRepository;

    @MockitoBean
    private StorageService storageService;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private TestUsers testUsers;

    private String authorization;

    @BeforeEach
    void signIn() {
        authorization = testUsers.createAndAuthorize("owner@example.com");
    }

    @BeforeEach
    void stubUpload() {
        // 실제 구현과 같은 규칙으로 키를 만들어 돌려준다: prescriptions/{publicId}.{확장자}
        given(storageService.upload(any(), anyString()))
                .willAnswer(invocation -> "prescriptions/" + invocation.getArgument(1) + ".png");
    }

    private MockMultipartFile document(String contentType, byte[] content) {
        return new MockMultipartFile("document", "prescription.png", contentType, content);
    }

    private MockMultipartFile validDocument() {
        return document("image/png", TestImages.png());
    }

    private static String publicIdFrom(MvcResult result) {
        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).startsWith(ENDPOINT + "/ext_");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Test
    void 업로드하면_202와_작업_위치와_세션_쿠키를_돌려준다() throws Exception {
        MvcResult result = mockMvc.perform(multipart(ENDPOINT).file(validDocument())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.completedAt").doesNotExist())
                .andExpect(jsonPath("$.data.result").doesNotExist())
                .andExpect(jsonPath("$.data.failure").doesNotExist())
                .andReturn();

        String publicId = publicIdFrom(result);
        PrescriptionExtraction saved = extractionRepository.findByPublicId(publicId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ExtractionStatus.PENDING);
        assertThat(saved.getCompletedAt()).isNull();
    }

    @Test
    void 원본_오브젝트_키는_작업_식별자로_짓는다() throws Exception {
        MvcResult result = mockMvc.perform(multipart(ENDPOINT).file(validDocument())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isAccepted())
                .andReturn();

        String publicId = publicIdFrom(result);
        PrescriptionExtraction saved = extractionRepository.findByPublicId(publicId).orElseThrow();
        assertThat(saved.getImageKey()).isEqualTo("prescriptions/" + publicId + ".png");
    }

    @Test
    void 작업은_요청한_사용자의_소유가_된다() throws Exception {
        String otherUser = testUsers.createAndAuthorize("other@example.com");

        MvcResult result = mockMvc.perform(multipart(ENDPOINT).file(validDocument())
                        .header(HttpHeaders.AUTHORIZATION, otherUser))
                .andExpect(status().isAccepted())
                .andReturn();

        PrescriptionExtraction saved =
                extractionRepository.findByPublicId(publicIdFrom(result)).orElseThrow();
        assertThat(saved.getOwnerKey()).startsWith("usr_");
        assertThat(saved.isOwnedBy("usr_someone-else")).isFalse();
    }

    @Test
    void 인증_없이_업로드하면_401() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(validDocument()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthorized"));
    }

    @Test
    void 위조된_토큰이면_401_invalid_token() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(validDocument())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("invalid_token"));
    }

    @Test
    void document_파트가_없으면_400_missing_document() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("missing_document"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 지원하지_않는_형식이면_415_unsupported_media_type() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(document("application/pdf", TestImages.png()))
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("unsupported_media_type"));
    }

    @Test
    void 디코드되지_않는_이미지면_422_invalid_image() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(document("image/png", "not an image".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("invalid_image"));
    }

    @Test
    void 검증에_실패하면_저장소에_업로드하지_않는다() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(document("image/png", "not an image".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnprocessableContent());

        org.mockito.Mockito.verify(storageService, org.mockito.Mockito.never())
                .upload(any(), anyString());
    }

    @Test
    void 작업을_만들면_분석_요청_이벤트를_발행한다() throws Exception {
        MvcResult result = mockMvc.perform(multipart(ENDPOINT).file(validDocument())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isAccepted())
                .andReturn();

        assertThat(events.stream(ExtractionRequestedEvent.class))
                .extracting(ExtractionRequestedEvent::publicId)
                .containsExactly(publicIdFrom(result));
    }

    @Test
    void 분석_요청은_커밋_이후에_전달된다() throws Exception {
        // 커밋 전에 넘기면 워커가 아직 저장되지 않은 작업을 조회해 "없는 작업"으로 처리해버린다.
        var listener = com.example.demo.domain.prescription.worker.ExtractionWorker.class
                .getMethod("onExtractionRequested", ExtractionRequestedEvent.class)
                .getAnnotation(org.springframework.transaction.event.TransactionalEventListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.phase())
                .isEqualTo(org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void 생성시각은_ISO_8601_UTC_문자열로_직렬화된다() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(validDocument())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isAccepted())
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                        ".*\"createdAt\":\"\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z\".*")));
    }
}
