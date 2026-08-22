package com.example.demo.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    private static final byte[] CONTENT = "가짜 처방전 바이트".getBytes(StandardCharsets.UTF_8);

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private final StorageProperties properties = new StorageProperties(
            "ak", "sk", "ap-chuncheon-1", "https://storage.test", "bucket");

    private StorageService storageService() {
        return new StorageService(s3Client, s3Presigner, properties);
    }

    /**
     * 톰캣이 넘겨주는 multipart 스트림은 되감기를 지원하지 않는다.
     * MockMultipartFile은 ByteArrayInputStream을 주므로 되감기가 되어 실제 조건과 다르다.
     */
    private static MultipartFile nonResettableFile(String filename, String contentType) {
        return new MockMultipartFile("document", filename, contentType, CONTENT) {
            @Override
            public InputStream getInputStream() {
                return new FilterInputStream(new ByteArrayInputStream(CONTENT)) {
                    @Override
                    public boolean markSupported() {
                        return false;
                    }

                    @Override
                    public synchronized void reset() throws IOException {
                        throw new IOException("mark/reset 미지원");
                    }
                };
            }
        };
    }

    private RequestBody captureUploadedBody(MultipartFile file) {
        storageService().upload(file, "ext_abc");

        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        then(s3Client).should().putObject(any(PutObjectRequest.class), body.capture());
        return body.getValue();
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        try (stream) {
            return stream.readAllBytes();
        }
    }

    @Test
    void 본문을_두_번_읽어도_매번_전체_내용이_나온다() throws IOException {
        // SDK는 서명과 재시도 때문에 본문을 여러 번 읽는다. 스트림 하나를 넘기면
        // 두 번째 읽기에서 "does not support mark/reset" 으로 깨진다.
        RequestBody body = captureUploadedBody(nonResettableFile("p.png", "image/png"));

        assertThat(readAll(body.contentStreamProvider().newStream())).isEqualTo(CONTENT);
        assertThat(readAll(body.contentStreamProvider().newStream())).isEqualTo(CONTENT);
    }

    @Test
    void 본문_길이를_실제_크기로_알려준다() {
        RequestBody body = captureUploadedBody(nonResettableFile("p.png", "image/png"));

        assertThat(body.optionalContentLength()).contains((long) CONTENT.length);
    }

    @Test
    void 오브젝트_키는_주어진_이름과_확장자로_만든다() {
        String key = storageService().upload(nonResettableFile("사진.PNG", "image/png"), "ext_abc");

        assertThat(key).isEqualTo("prescriptions/ext_abc.png");
    }

    @Test
    void 컨텐츠_타입이_없으면_octet_stream으로_올린다() {
        storageService().upload(nonResettableFile("p", null), "ext_abc");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        then(s3Client).should().putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().contentType()).isEqualTo("application/octet-stream");
        assertThat(request.getValue().bucket()).isEqualTo("bucket");
    }
}
