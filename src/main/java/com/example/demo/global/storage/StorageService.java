package com.example.demo.global.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.global.exception.CustomException;
import com.example.demo.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private static final String PRESCRIPTION_PREFIX = "prescriptions/";
    private static final Duration PRESIGNED_URL_TTL = Duration.ofMinutes(10);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    public String upload(MultipartFile file) {
        return upload(file, UUID.randomUUID().toString());
    }

    /**
     * 오브젝트 이름을 호출자가 정한다. 작업 식별자를 그대로 넘기면 스토리지에 남은 파일만 보고도
     * 어느 작업 것인지 대조할 수 있다.
     */
    public String upload(MultipartFile file, String objectName) {
        String objectKey = PRESCRIPTION_PREFIX + objectName + extensionOf(file.getOriginalFilename());

        try (InputStream content = file.getInputStream()) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.bucketName())
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(content, file.getSize()));
            return objectKey;

        } catch (IOException | SdkException e) {
            log.error("오브젝트 업로드 실패: key={}", objectKey, e);
            throw new CustomException(ErrorCode.FILE_STORAGE_EXCEPTION);
        }
    }

    /** 분석 업스트림에 올리기 위해 원본을 다시 읽어온다. */
    public byte[] download(String objectKey) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.bucketName())
                    .key(objectKey)
                    .build()).asByteArray();
        } catch (SdkException e) {
            log.error("오브젝트 조회 실패: key={}", objectKey, e);
            throw new CustomException(ErrorCode.FILE_STORAGE_EXCEPTION);
        }
    }

    /**
     * 보상 삭제나 사후 청소처럼 이미 실패 경로에 있는 호출자를 위해 예외를 던지지 않는다.
     * 지우지 못한 오브젝트는 버킷 라이프사이클 정책이 만료시키는 것을 전제로 한다.
     */
    public boolean deleteQuietly(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucketName())
                    .key(objectKey)
                    .build());
            return true;
        } catch (SdkException e) {
            log.warn("오브젝트 삭제 실패, 라이프사이클 만료에 맡긴다: key={}", objectKey, e);
            return false;
        }
    }

    public String createPresignedUrl(String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_TTL)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * 업로드한 파일명은 키에 넣지 않는다. 경로 구분자나 비ASCII 문자가 그대로 들어가는 것을 막기 위해
     * 확장자만, 그것도 형태가 멀쩡할 때만 취한다.
     */
    private static String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        String extension = originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,8}") ? "." + extension : "";
    }
}
