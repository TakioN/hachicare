package com.example.demo.domain.prescription.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.domain.prescription.entity.ExtractionFailureCode;
import com.example.demo.domain.prescription.entity.PrescriptionExtraction;
import com.example.demo.domain.prescription.repository.PrescriptionExtractionRepository;
import com.example.demo.support.TestResults;
import com.example.demo.support.TestUsers;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PrescriptionExtractionQueryControllerTest {

    private static final String ENDPOINT = "/api/v1/prescription-extractions";
    private static final Instant CREATED_AT = Instant.parse("2026-08-22T10:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-22T10:00:04Z");
    private static final String RESULT_JSON = """
            {"reviewStatus":"ready","documentType":"patient_copy_prescription",\
            "medications":[{"id":"med_1"}],"issues":[]}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrescriptionExtractionRepository extractionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestUsers testUsers;

    private String owner;
    private String authorization;

    @BeforeEach
    void signIn() {
        var user = testUsers.create("owner@example.com");
        owner = user.getPublicId();
        authorization = testUsers.bearerFor(owner);
    }

    private PrescriptionExtraction savePending() {
        return extractionRepository.save(PrescriptionExtraction.pending(
                PrescriptionExtraction.newPublicId(), owner, "prescriptions/x.png", CREATED_AT));
    }


    @Test
    void pending_작업은_결과도_실패도_완료시각도_없이_돌아온다() throws Exception {
        PrescriptionExtraction extraction = savePending();

        mockMvc.perform(get(ENDPOINT + "/" + extraction.getPublicId()).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(extraction.getPublicId()))
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-08-22T10:00:00Z"))
                .andExpect(jsonPath("$.data.completedAt").doesNotExist())
                .andExpect(jsonPath("$.data.result").doesNotExist())
                .andExpect(jsonPath("$.data.failure").doesNotExist());
    }

    @Test
    void completed_작업은_결과를_문자열이_아닌_객체로_싣는다() throws Exception {
        PrescriptionExtraction extraction = savePending();
        extraction.complete(RESULT_JSON, COMPLETED_AT);

        mockMvc.perform(get(ENDPOINT + "/" + extraction.getPublicId()).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("completed"))
                .andExpect(jsonPath("$.data.completedAt").value("2026-08-22T10:00:04Z"))
                .andExpect(jsonPath("$.data.result.reviewStatus").value("ready"))
                .andExpect(jsonPath("$.data.result.documentType").value("patient_copy_prescription"))
                .andExpect(jsonPath("$.data.result.medications[0].id").value("med_1"))
                .andExpect(jsonPath("$.data.result.issues").isArray())
                .andExpect(jsonPath("$.data.failure").doesNotExist());
    }

    @Test
    void failed_작업은_실패코드와_문구를_싣고_결과는_없다() throws Exception {
        PrescriptionExtraction extraction = savePending();
        extraction.fail(ExtractionFailureCode.UPSTREAM_UNAVAILABLE, COMPLETED_AT);

        mockMvc.perform(get(ENDPOINT + "/" + extraction.getPublicId()).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.completedAt").value("2026-08-22T10:00:04Z"))
                .andExpect(jsonPath("$.data.failure.code").value("upstream_unavailable"))
                .andExpect(jsonPath("$.data.failure.message").value("처방전을 분석하지 못했습니다."))
                .andExpect(jsonPath("$.data.result").doesNotExist());
    }

    @Test
    void 저장했다_읽어도_결과_구조가_그대로다() throws Exception {
        PrescriptionExtraction extraction = savePending();
        extraction.complete(objectMapper.writeValueAsString(TestResults.valid()), COMPLETED_AT);

        mockMvc.perform(get(ENDPOINT + "/" + extraction.getPublicId()).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.reviewStatus").value("ready"))
                .andExpect(jsonPath("$.data.result.medications[0].id").value("med_1"))
                .andExpect(jsonPath("$.data.result.medications[0].drugName.status").value("extracted"))
                .andExpect(jsonPath("$.data.result.medications[0].drugName.confidence").value("high"))
                .andExpect(jsonPath("$.data.result.medications[0].dose.value.unit").value("정"))
                .andExpect(jsonPath("$.data.result.medications[0].dose.value.rawText").value("1정"))
                .andExpect(jsonPath("$.data.result.medications[0].frequencyPerDay.value").value(1))
                .andExpect(jsonPath("$.data.result.medications[0].timingInstruction.english")
                        .value("30 minutes after breakfast"))
                // 값이 없는 선택 필드는 응답에 아예 나오지 않아야 한다
                .andExpect(jsonPath("$.data.result.medications[0].route").doesNotExist())
                .andExpect(jsonPath("$.data.result.medications[0].drugName.evidence").doesNotExist());
    }

    @Test
    void 다른_세션의_작업은_403_forbidden() throws Exception {
        PrescriptionExtraction extraction = savePending();

        mockMvc.perform(get(ENDPOINT + "/" + extraction.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, testUsers.createAndAuthorize("other@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }


    @Test
    void 존재하지_않는_작업은_404_extraction_not_found() throws Exception {
        mockMvc.perform(get(ENDPOINT + "/ext_doesnotexist").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("extraction_not_found"));
    }

    @Test
    void 인증_없이_조회하면_401() throws Exception {
        PrescriptionExtraction extraction = savePending();

        mockMvc.perform(get(ENDPOINT + "/" + extraction.getPublicId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthorized"));
    }

}
