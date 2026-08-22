package com.example.demo.domain.prescription.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.domain.prescription.entity.ExtractionStatus;
import com.example.demo.domain.prescription.entity.PrescriptionExtraction;
import com.example.demo.domain.prescription.repository.PrescriptionExtractionRepository;
import com.example.demo.global.session.AnonymousSessionManager;
import com.example.demo.global.storage.StorageService;
import com.example.demo.support.TestImages;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PrescriptionExtractionControllerTest {

    private static final String ENDPOINT = "/api/v1/prescription-extractions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrescriptionExtractionRepository extractionRepository;

    @MockitoBean
    private StorageService storageService;

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
        MvcResult result = mockMvc.perform(multipart(ENDPOINT).file(validDocument()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.completedAt").doesNotExist())
                .andExpect(jsonPath("$.data.result").doesNotExist())
                .andExpect(jsonPath("$.data.failure").doesNotExist())
                .andExpect(cookie().exists(AnonymousSessionManager.COOKIE_NAME))
                .andExpect(cookie().httpOnly(AnonymousSessionManager.COOKIE_NAME, true))
                .andReturn();

        String publicId = publicIdFrom(result);
        PrescriptionExtraction saved = extractionRepository.findByPublicId(publicId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ExtractionStatus.PENDING);
        assertThat(saved.getCompletedAt()).isNull();
    }

    @Test
    void 원본_오브젝트_키는_작업_식별자로_짓는다() throws Exception {
        MvcResult result = mockMvc.perform(multipart(ENDPOINT).file(validDocument()))
                .andExpect(status().isAccepted())
                .andReturn();

        String publicId = publicIdFrom(result);
        PrescriptionExtraction saved = extractionRepository.findByPublicId(publicId).orElseThrow();
        assertThat(saved.getImageKey()).isEqualTo("prescriptions/" + publicId + ".png");
    }

    @Test
    void 세션_쿠키가_이미_있으면_새로_발급하지_않고_그_세션의_작업으로_만든다() throws Exception {
        MvcResult result = mockMvc.perform(multipart(ENDPOINT)
                        .file(validDocument())
                        .cookie(new Cookie(AnonymousSessionManager.COOKIE_NAME, "existing-session")))
                .andExpect(status().isAccepted())
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isEmpty();

        PrescriptionExtraction saved =
                extractionRepository.findByPublicId(publicIdFrom(result)).orElseThrow();
        assertThat(saved.isOwnedBy("existing-session")).isTrue();
    }

    @Test
    void document_파트가_없으면_400_missing_document() throws Exception {
        mockMvc.perform(multipart(ENDPOINT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("missing_document"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 지원하지_않는_형식이면_415_unsupported_media_type() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(document("application/pdf", TestImages.png())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("unsupported_media_type"));
    }

    @Test
    void 디코드되지_않는_이미지면_422_invalid_image() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(document("image/png", "not an image".getBytes())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("invalid_image"));
    }

    @Test
    void 검증에_실패하면_저장소에_업로드하지_않는다() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(document("image/png", "not an image".getBytes())))
                .andExpect(status().isUnprocessableContent());

        org.mockito.Mockito.verify(storageService, org.mockito.Mockito.never())
                .upload(any(), anyString());
    }

    @Test
    void 생성시각은_ISO_8601_UTC_문자열로_직렬화된다() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(validDocument()))
                .andExpect(status().isAccepted())
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                        ".*\"createdAt\":\"\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z\".*")));
    }
}
