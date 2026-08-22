package com.example.demo.domain.prescription.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.global.exception.CustomException;
import com.example.demo.global.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PrescriptionImageValidator {

    private static final Set<String> DECODABLE_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    /** ImageIO가 읽지 못하는 형식. 내용 검사는 컨테이너 헤더로 대신한다. */
    private static final Set<String> HEIF_CONTENT_TYPES = Set.of("image/heic", "image/heif");

    /** ISO base media file format의 ftyp 박스에서 HEIF 계열임을 나타내는 브랜드들. */
    private static final Set<String> HEIF_BRANDS = Set.of(
            "heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1");

    private static final int FTYP_MARKER_OFFSET = 4;
    private static final int MAJOR_BRAND_OFFSET = 8;
    private static final int COMPATIBLE_BRANDS_OFFSET = 16;
    private static final int BRAND_LENGTH = 4;
    private static final int HEADER_BYTES = 64;

    /**
     * 허용 크기 초과(413)는 multipart 설정에서 먼저 걸리므로 여기서 다루지 않는다.
     * 문서 순서대로 없음(400) → 형식(415) → 내용(422)으로 판정한다.
     */
    public void validate(MultipartFile document) {
        if (document == null || document.isEmpty()) {
            throw new CustomException(ErrorCode.MISSING_DOCUMENT);
        }

        String contentType = baseContentType(document);
        try {
            if (DECODABLE_CONTENT_TYPES.contains(contentType)) {
                requireDecodable(document);
            } else if (HEIF_CONTENT_TYPES.contains(contentType)) {
                requireHeifContainer(document);
            } else {
                throw new CustomException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            }
        } catch (CustomException e) {
            // 왜 거부됐는지 남기지 않으면 사용자도 우리도 원인을 알 수 없다.
            // 선두 바이트는 형식 식별자일 뿐이라 처방전 내용이 새지 않는다.
            log.warn("이미지 거부: code={} declaredType={} size={} magic={}",
                    e.getErrorCode().getCode(), document.getContentType(),
                    document.getSize(), magicBytes(document));
            throw e;
        }
    }

    /** 선두 12바이트를 16진수로. 실제 형식이 무엇인지 판별하는 데 쓴다. */
    private static String magicBytes(MultipartFile document) {
        try (InputStream content = document.getInputStream()) {
            byte[] head = content.readNBytes(12);
            StringBuilder hex = new StringBuilder();
            for (byte b : head) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (IOException e) {
            return "읽기실패";
        }
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

    /**
     * HEIC/HEIF는 ImageIO가 디코드하지 못한다. 디코더를 붙이는 대신 컨테이너 헤더만 확인한다.
     * 우리가 픽셀을 볼 일은 없고 실제 해석은 분석 업스트림이 한다.
     */
    private static void requireHeifContainer(MultipartFile document) {
        byte[] header = readHeader(document);

        if (header.length < MAJOR_BRAND_OFFSET + BRAND_LENGTH
                || !"ftyp".equals(ascii(header, FTYP_MARKER_OFFSET))) {
            throw new CustomException(ErrorCode.INVALID_IMAGE);
        }
        if (isHeifBrand(ascii(header, MAJOR_BRAND_OFFSET))) {
            return;
        }
        // major brand가 일반 값이어도 compatible brands에 HEIF 계열이 들어 있을 수 있다.
        for (int offset = COMPATIBLE_BRANDS_OFFSET; offset + BRAND_LENGTH <= header.length;
                offset += BRAND_LENGTH) {
            if (isHeifBrand(ascii(header, offset))) {
                return;
            }
        }
        throw new CustomException(ErrorCode.INVALID_IMAGE);
    }

    private static byte[] readHeader(MultipartFile document) {
        try (InputStream content = document.getInputStream()) {
            return content.readNBytes(HEADER_BYTES);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INVALID_IMAGE);
        }
    }

    private static String ascii(byte[] header, int offset) {
        return new String(header, offset, BRAND_LENGTH, StandardCharsets.US_ASCII);
    }

    private static boolean isHeifBrand(String brand) {
        return HEIF_BRANDS.contains(brand.toLowerCase(Locale.ROOT));
    }
}
