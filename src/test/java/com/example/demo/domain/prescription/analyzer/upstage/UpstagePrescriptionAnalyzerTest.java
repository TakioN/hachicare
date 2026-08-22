package com.example.demo.domain.prescription.analyzer.upstage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class UpstagePrescriptionAnalyzerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final JsonNode LIVE_RESPONSES = loadLiveResponses();
    private static final String BASE_URL = "https://upstage.test/v2";
    private static final String API_KEY = "up-test-key";
    private static final String AGENT_ID = "agt_test123";
    private static final String IMAGE_KEY = "prescriptions/ext_abc.png";

    private static final String UPLOADED = """
            {"id":"file-abc123","object":"file","filename":"ext_abc.png"}""";
    private static final String QUEUED = """
            {"id":"response-xyz","object":"response","status":"queued","output":null}""";
    private static final String IN_PROGRESS = """
            {"id":"response-xyz","object":"response","status":"in_progress","output":null}""";
    private static final String COMPLETED = JSON.writeValueAsString(LIVE_RESPONSES.get("clear"));

    @Mock
    private StorageService storageService;

    private MockRestServiceServer server;
    private UpstagePrescriptionAnalyzer analyzer;

    private static JsonNode loadLiveResponses() {
        InputStream resource = Objects.requireNonNull(
                UpstagePrescriptionAnalyzerTest.class.getResourceAsStream("/upstage/live-responses.json"));
        return JSON.readTree(resource);
    }

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
                .andExpect(jsonPath("$.include[0]").value("all"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private void expectPoll(ExpectedCount count, String response) {
        server.expect(count, requestTo(BASE_URL + "/responses/response-xyz?include%5B%5D=all"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private void expectDelete() {
        server.expect(requestTo(BASE_URL + "/files/file-abc123"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());
    }

    private void expectDeleteFailure() {
        server.expect(requestTo(BASE_URL + "/files/file-abc123"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());
    }

    private void assertFailsWith(ExtractionFailureCode expected) {
        assertThatThrownBy(() -> analyzer.analyze(IMAGE_KEY))
                .isInstanceOf(AnalysisFailedException.class)
                .extracting(e -> ((AnalysisFailedException) e).getFailureCode())
                .isEqualTo(expected);
        server.verify();
    }

    @Test
    void 업로드하고_실행하고_완료될_때까지_기다린다() {
        givenStoredImage();
        expectUpload();
        expectCreate(IN_PROGRESS);
        expectPoll(ExpectedCount.once(), COMPLETED);
        expectDelete();

        PrescriptionExtractionResult result = analyzer.analyze(IMAGE_KEY);

        assertThat(result.reviewStatus()).isEqualTo(ReviewStatus.NEEDS_REVIEW);
        server.verify();
    }

    @Test
    void waitsForQueuedJob() {
        givenStoredImage();
        expectUpload();
        expectCreate(QUEUED);
        expectPoll(ExpectedCount.once(), COMPLETED);
        expectDelete();

        assertThat(analyzer.analyze(IMAGE_KEY).reviewStatus()).isEqualTo(ReviewStatus.NEEDS_REVIEW);
        server.verify();
    }

    @Test
    void 실행_응답이_이미_완료면_폴링하지_않는다() {
        givenStoredImage();
        expectUpload();
        expectCreate(COMPLETED);
        expectDelete();

        assertThat(analyzer.analyze(IMAGE_KEY).reviewStatus()).isEqualTo(ReviewStatus.NEEDS_REVIEW);
        server.verify();
    }

    @Test
    void 에이전트가_실패로_끝나면_upstream_unavailable() {
        givenStoredImage();
        expectUpload();
        expectCreate("""
                {"id":"response-xyz","status":"failed","output":null}""");
        expectDelete();

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
    void deletesUploadedFileWhenCreateCallFails() {
        givenStoredImage();
        expectUpload();
        server.expect(requestTo(BASE_URL + "/responses"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        expectDelete();

        assertFailsWith(ExtractionFailureCode.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void deletesUploadedFileWhenPollingCallFails() {
        givenStoredImage();
        expectUpload();
        expectCreate(IN_PROGRESS);
        server.expect(requestTo(BASE_URL + "/responses/response-xyz?include%5B%5D=all"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());
        expectDelete();

        assertFailsWith(ExtractionFailureCode.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void 계속_processing이면_기다리다_upstream_timeout() {
        // 볼 때마다 4초씩 흐르는 시계에 10초 상한
        setUpWith(tickingClock(Duration.ofSeconds(4)), Duration.ofSeconds(10));
        givenStoredImage();

        expectUpload();
        expectCreate(IN_PROGRESS);
        expectPoll(ExpectedCount.manyTimes(), IN_PROGRESS);
        expectDelete();

        assertFailsWith(ExtractionFailureCode.UPSTREAM_TIMEOUT);
    }

    @Test
    void rejectsUnknownJobStatus() {
        givenStoredImage();
        expectUpload();
        expectCreate("""
                {"id":"response-xyz","status":"processing","output":null}""");
        expectDelete();

        assertFailsWith(ExtractionFailureCode.INVALID_AGENT_RESPONSE);
    }

    @Test
    void 출력이_계약과_다르면_invalid_agent_response() {
        givenStoredImage();
        expectUpload();
        expectCreate("""
                {"id":"response-xyz","status":"completed","output":{"foo":"bar"}}""");
        expectDelete();

        assertFailsWith(ExtractionFailureCode.INVALID_AGENT_RESPONSE);
    }

    @Test
    void preservesAnalysisFailureWhenFileDeletionAlsoFails() {
        givenStoredImage();
        expectUpload();
        expectCreate("""
                {"id":"response-xyz","status":"completed","output":{"foo":"bar"}}""");
        expectDeleteFailure();

        Throwable thrown = catchThrowable(() -> analyzer.analyze(IMAGE_KEY));

        assertThat(thrown).isInstanceOf(AnalysisFailedException.class);
        assertThat(((AnalysisFailedException) thrown).getFailureCode())
                .isEqualTo(ExtractionFailureCode.INVALID_AGENT_RESPONSE);
        assertThat(thrown.getSuppressed()).singleElement()
                .isInstanceOf(AnalysisFailedException.class);
        server.verify();
    }

    @Test
    void HEIC도_제_컨텐츠_타입으로_올라간다() {
        String heicKey = "prescriptions/ext_abc.heic";
        given(storageService.download(heicKey)).willReturn(new byte[] {1, 2, 3});
        expectUpload("image/heic", "ext_abc.heic");
        expectCreate(COMPLETED);
        expectDelete();

        assertThat(analyzer.analyze(heicKey).reviewStatus()).isEqualTo(ReviewStatus.NEEDS_REVIEW);
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
