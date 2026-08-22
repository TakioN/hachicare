package com.example.demo.domain.medication.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.domain.medication.entity.MedicationStatus;
import com.example.demo.domain.medication.repository.MedicationRepository;
import com.example.demo.support.TestUsers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MedicationControllerTest {

    private static final String ENDPOINT = "/api/v1/medications";

    private static final String TWO_MEDICATIONS = """
            {
              "sourceExtractionId": "ext_abc",
              "medications": [
                {"drugName":"아모잘탄정 5/50mg","doseValue":1,"doseUnit":"정",
                 "frequencyPerDay":1,"durationDays":30,"timingInstruction":"아침 식후 30분",
                 "startedOn":"2026-08-23"},
                {"drugName":"타이레놀","doseValue":2,"doseUnit":"정","frequencyPerDay":3}
              ]
            }""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MedicationRepository medicationRepository;

    @Autowired
    private TestUsers testUsers;

    private String authorization;

    @BeforeEach
    void signIn() {
        authorization = testUsers.createAndAuthorize("owner@example.com");
    }

    private MvcResult postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private String createTwoAndGetFirstId() throws Exception {
        return dataOf(postJson(ENDPOINT, TWO_MEDICATIONS)).get(0).get("id").stringValue();
    }

    @Test
    void 목록을_한_번에_등록한다() throws Exception {
        MvcResult result = postJson(ENDPOINT, TWO_MEDICATIONS);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode created = dataOf(result);
        assertThat(created.size()).isEqualTo(2);
        assertThat(created.get(0).get("id").stringValue()).startsWith("med_");
        assertThat(created.get(0).get("drugName").stringValue()).isEqualTo("아모잘탄정 5/50mg");
        assertThat(created.get(0).get("sourceExtractionId").stringValue()).isEqualTo("ext_abc");
        assertThat(created.get(0).get("status").stringValue()).isEqualTo("active");
        assertThat(created.get(0).get("startedOn").stringValue()).isEqualTo("2026-08-23");
    }

    @Test
    void 값이_없는_항목은_응답에서_빠진다() throws Exception {
        JsonNode second = dataOf(postJson(ENDPOINT, TWO_MEDICATIONS)).get(1);

        assertThat(second.has("durationDays")).isFalse();
        assertThat(second.has("startedOn")).isFalse();
    }

    @Test
    void 하나라도_잘못되면_전부_등록하지_않는다() throws Exception {
        String withBlankName = """
                {"medications":[{"drugName":"정상약"},{"drugName":"  "}]}""";

        MvcResult result = postJson(ENDPOINT, withBlankName);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(dataOf(result)).isNull();
        assertThat(medicationRepository.findAll()).isEmpty();
    }

    @Test
    void 빈_목록은_400() throws Exception {
        mockMvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"medications\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("empty_medications"));
    }

    @Test
    void 같은_분석을_두_번_등록하면_409() throws Exception {
        postJson(ENDPOINT, TWO_MEDICATIONS);

        mockMvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(TWO_MEDICATIONS))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("extraction_already_confirmed"));
    }

    @Test
    void 출처_없이_손으로_추가할_수_있다() throws Exception {
        String manual = """
                {"medications":[{"drugName":"비타민D"}]}""";

        JsonNode created = dataOf(postJson(ENDPOINT, manual)).get(0);

        assertThat(created.has("sourceExtractionId")).isFalse();
        assertThat(created.get("drugName").stringValue()).isEqualTo("비타민D");
    }

    @Test
    void 상태_필터로_복용_중인_것만_받는다() throws Exception {
        String finishedId = createTwoAndGetFirstId();
        postJson(ENDPOINT + "/" + finishedId + "/finish", "{\"endedOn\":\"2026-09-01\"}");

        mockMvc.perform(get(ENDPOINT).param("status", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("active"));
    }

    @Test
    void 필터를_주지_않으면_종료한_약까지_전부_받는다() throws Exception {
        String finishedId = createTwoAndGetFirstId();
        postJson(ENDPOINT + "/" + finishedId + "/finish", "{\"endedOn\":\"2026-09-01\"}");

        mockMvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void 남의_약은_목록에_섞이지_않는다() throws Exception {
        postJson(ENDPOINT, TWO_MEDICATIONS);

        mockMvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION,
                        testUsers.createAndAuthorize("other@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 수정하면_보낸_값으로_전체_교체된다() throws Exception {
        String id = createTwoAndGetFirstId();

        mockMvc.perform(put(ENDPOINT + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"drugName\":\"바뀐약\",\"frequencyPerDay\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.drugName").value("바뀐약"))
                .andExpect(jsonPath("$.data.frequencyPerDay").value(2))
                // 보내지 않은 항목은 비워진다
                .andExpect(jsonPath("$.data.durationDays").doesNotExist())
                .andExpect(jsonPath("$.data.startedOn").doesNotExist());
    }

    @Test
    void 복용을_끝내도_목록에_남는다() throws Exception {
        String id = createTwoAndGetFirstId();

        mockMvc.perform(post(ENDPOINT + "/" + id + "/finish")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endedOn\":\"2026-09-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("finished"))
                .andExpect(jsonPath("$.data.endedOn").value("2026-09-01"));

        assertThat(medicationRepository.findByPublicId(id)).isPresent();
    }

    @Test
    void 종료일을_주지_않으면_서비스_시간대_기준_오늘로_잡는다() throws Exception {
        // UTC로 계산하면 한국 새벽 0~9시에 어제가 찍혀, 오늘 시작한 약을 끝낼 수 없다.
        String id = createTwoAndGetFirstId();

        MvcResult result = mockMvc.perform(post(ENDPOINT + "/" + id + "/finish")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(dataOf(result).get("endedOn").stringValue())
                .isEqualTo(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString());
    }

    @Test
    void 삭제하면_사라진다() throws Exception {
        String id = createTwoAndGetFirstId();

        mockMvc.perform(delete(ENDPOINT + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNoContent());

        assertThat(medicationRepository.findByPublicId(id)).isEmpty();
    }

    @Test
    void 남의_약은_수정도_삭제도_못_한다() throws Exception {
        String id = createTwoAndGetFirstId();
        String intruder = testUsers.createAndAuthorize("intruder@example.com");

        mockMvc.perform(put(ENDPOINT + "/" + id).header(HttpHeaders.AUTHORIZATION, intruder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"drugName\":\"탈취\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));

        mockMvc.perform(delete(ENDPOINT + "/" + id).header(HttpHeaders.AUTHORIZATION, intruder))
                .andExpect(status().isForbidden());
    }

    @Test
    void 없는_약은_404() throws Exception {
        mockMvc.perform(delete(ENDPOINT + "/med_nope")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("medication_not_found"));
    }

    @Test
    void 인증_없이는_아무것도_못_한다() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthorized"));

        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(TWO_MEDICATIONS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 등록한_상태값은_문서_표기를_따른다() throws Exception {
        assertThat(MedicationStatus.ACTIVE.getValue()).isEqualTo("active");
        assertThat(MedicationStatus.FINISHED.getValue()).isEqualTo("finished");
    }
}
