package com.example.demo.domain.prescription.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.global.exception.CustomException;
import com.example.demo.global.exception.ErrorCode;

@Component
public class PrescriptionImageValidator {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    /**
     * 허용 크기 초과(413)는 multipart 설정에서 먼저 걸리므로 여기서 다루지 않는다.
     * 문서 순서대로 없음(400) → 형식(415) → decode(422)로 판정한다.
     */
    public void validate(MultipartFile document) {
        if (document == null || document.isEmpty()) {
            throw new CustomException(ErrorCode.MISSING_DOCUMENT);
        }
        if (!ALLOWED_CONTENT_TYPES.contains(baseContentType(document))) {
            throw new CustomException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        requireDecodable(document);
    }

    private static String baseContentType(MultipartFile document) {
        String contentType = document.getContentType();
        if (contentType == null) {
            return "";
        }
        int parameterStart = contentType.indexOf(';');
        String base = parameterStart < 0 ? contentType : contentType.substring(0, parameterStart);
        return base.trim().toLowerCase(Locale.ROOT);
    }

    /** Content-Type 헤더는 클라이언트가 조작할 수 있으므로 실제로 디코드되는지까지 확인한다. */
    private static void requireDecodable(MultipartFile document) {
        try (InputStream content = document.getInputStream()) {
            BufferedImage image = ImageIO.read(content);
            if (image == null) {
                throw new CustomException(ErrorCode.INVALID_IMAGE);
            }
            image.flush();
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INVALID_IMAGE);
        }
    }
}
