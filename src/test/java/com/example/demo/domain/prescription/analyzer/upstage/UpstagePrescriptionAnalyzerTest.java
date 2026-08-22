package com.example.demo.domain.prescription.analyzer.upstage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.example.demo.domain.prescription.analyzer.AnalysisFailedException;
import com.example.demo.domain.prescription.dto.result.PrescriptionExtractionResult;
import com.example.demo.domain.prescription.dto.result.ReviewStatus;
import com.example.demo.domain.prescription.entity.ExtractionFailureCode;
import com.example.demo.global.storage.StorageService;

@ExtendWith(MockitoExtension.class)
class UpstagePrescriptionAnalyzerTest {

    private static final String BASE_URL = "https://upstage.test/v2";
    private static final String API_KEY = "up-test-key";
    private static final String AGENT_ID = "agt_test123";
    private static final String IMAGE_KEY = "prescriptions/ext_abc.png";

    private static final String UPLOADED = """
            {"id":"file-abc123","object":"file","filename":"ext_abc.png"}""";
    private static final String PROCESSING = """
            {"id":"response-xyz","object":"response","status":"processing","output":null}""";
    private static final String COMPLETED = """
            {"id":"response-xyz","status":"completed","output":{
               "reviewStatus":"ready","documentType":"patient_copy_prescription",
               "medications":[],"issues":[]}}""";

    @Mock
    private StorageService storageService;

    private MockRestServiceServer server;
    private UpstagePrescriptionAnalyzer analyzer;

    /** 폴링 타임아웃을 재현하려면 시간이 흘러야 한다. 볼 때마다 일정량 전진하는 시계. */
    private static Clock tickingClock(Duration step) {
        return new Clock() {
            private Instant now = Instant.parse("2026-08-22T10:00:00Z");

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                Instant current = now;
                now = now.plus(step);
                return current;
            }
        };
    }

    private void setUpWith(Clock clock, Duration pollTimeout) {
        UpstageProperties properties = new UpstageProperties(
                BASE_URL, API_KEY, AGENT_ID,
                Duration.ofSeconds(5), Duration.ofMillis(1), pollTimeout);

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        RestClient restClient = new UpstageClientConfig(properties).decorate(builder).build();

        analyzer = new UpstagePrescriptionAnalyzer(
                storageService,
                new UpstageAgentClient(restClient, properties),
                new UpstageResponseMapper(),
                properties,
                clock);
    }

    @BeforeEach
    void setUp() {
        setUpWith(Clock.fixed(Instant.parse("2026-08-22T10:00:00Z"), ZoneOffset.UTC),
                Duration.ofSeconds(90));
    }

    private void givenStoredImage() {
        given(storageService.download(IMAGE_KEY)).willReturn(new byte[] {1, 2, 3});
    }

    private void expectUpload() {
        expectUpload("image/png", "ext_abc.png");
    }

    private void expectUpload(String partContentType, String filename) {
        server.expect(requestTo(BASE_URL + "/files"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                // 파트 헤더까지 확인한다. 확장자 추론에 맡기면 HEIC가 octet-stream으로 나간다.
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Content-Type: " + partContentType),
                        org.hamcrest.Matchers.containsString("filename=\"" + filename + "\""),
                        org.hamcrest.Matchers.containsString("user_data"))))
                .andRespond(withSuccess(UPLOADED, MediaType.APPLICATION_JSON));
    }

    private void expectCreate(String response) {
        server.expect(requestTo(BASE_URL + "/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                .andExpect(jsonPath("$.model").value(AGENT_ID))
                .andExpect(jsonPath("$.input[0].content[0].type").value("input_file"))
                .andExpect(jsonPath("$.input[0].content[0].file_id").value("file-abc123"))
                .andExpect(jsonPath("$.include[0]").value("last"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private void expectPoll(ExpectedCount count, String response) {
        server.expect(count, requestTo(BASE_URL + "/responses/response-xyz"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private void assertFailsWith(ExtractionFailureCode expected) {
        assertThatThrownBy(() -> analyzer.analyze(IMAGE_KEY))
                .isInstanceOf(AnalysisFailedException.class)
                .extracting(e -> ((AnalysisFailedException) e).getFailureCode())
                .isEqualTo(expected);
    }

    @Test
    void 업로드하고_실행하고_완료될_때까지_기다린다() {
        givenStoredImage();
        expectUpload();
        expectCreate(PROCESSING);
        expectPoll(ExpectedCount.once(), COMPLETED);

        PrescriptionExtractionResult result = analyzer.analyze(IMAGE_KEY);

        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.READY);
        server.verify();
    }

    @Test
    void 실행_응답이_이미_완료면_폴링하지_않는다() {
        givenStoredImage();
        expectUpload();
        expectCreate(COMPLETED);

        assertThat(analyzer.analyze(IMAGE_KEY).reviewStatus()).isEqualTo(ReviewStatus.READY);
        server.verify();
    }

    @Test
    void 에이전트가_실패로_끝나면_upstream_unavailable() {
        givenStoredImage();
        expectUpload();
        expectCreate("""
                {"id":"response-xyz","status":"failed","output":null}""");

        assertFailsWith(ExtractionFailureCode.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void 업스트림이_5xx면_upstream_unavailable() {
        givenStoredImage();
        server.expect(requestTo(BASE_URL + "/files"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertFailsWith(ExtractionFailureCode.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void 계속_processing이면_기다리다_upstream_timeout() {
        // 볼 때마다 4초씩 흐르는 시계에 10초 상한
        setUpWith(tickingClock(Duration.ofSeconds(4)), Duration.ofSeconds(10));
        givenStoredImage();

        expectUpload();
        expectCreate(PROCESSING);
        expectPoll(ExpectedCount.manyTimes(), PROCESSING);

        assertFailsWith(ExtractionFailureCode.UPSTREAM_TIMEOUT);
    }

    @Test
    void 출력이_계약과_다르면_invalid_agent_response() {
        givenStoredImage();
        expectUpload();
        expectCreate("""
                {"id":"response-xyz","status":"completed","output":{"foo":"bar"}}""");

        assertFailsWith(ExtractionFailureCode.INVALID_AGENT_RESPONSE);
    }

    @Test
    void HEIC도_제_컨텐츠_타입으로_올라간다() {
        String heicKey = "prescriptions/ext_abc.heic";
        given(storageService.download(heicKey)).willReturn(new byte[] {1, 2, 3});
        expectUpload("image/heic", "ext_abc.heic");
        expectCreate(COMPLETED);

        assertThat(analyzer.analyze(heicKey).reviewStatus()).isEqualTo(ReviewStatus.READY);
        server.verify();
    }

    @Test
    void 원본을_읽지_못하면_업스트림을_호출하지_않는다() {
        given(storageService.download(IMAGE_KEY))
                .willThrow(new IllegalStateException("스토리지 장애"));

        assertThatThrownBy(() -> analyzer.analyze(IMAGE_KEY))
                .isInstanceOf(IllegalStateException.class);
        server.verify();
    }
}
