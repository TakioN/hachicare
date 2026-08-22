package com.example.demo.domain.prescription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.global.exception.CustomException;
import com.example.demo.global.exception.ErrorCode;
import com.example.demo.support.TestImages;

class PrescriptionImageValidatorTest {

    private final PrescriptionImageValidator validator = new PrescriptionImageValidator();

    private static MultipartFile file(String contentType, byte[] content) {
        return new MockMultipartFile("document", "prescription.png", contentType, content);
    }

    private static void assertRejectedWith(MultipartFile document, ErrorCode expected) {
        assertThatThrownBy(() -> new PrescriptionImageValidator().validate(document))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @Test
    void 정상적인_png는_통과한다() {
        assertThatCode(() -> validator.validate(file("image/png", TestImages.png())))
                .doesNotThrowAnyException();
    }

    @Test
    void 컨텐츠_타입_대소문자와_파라미터는_무시한다() {
        assertThatCode(() -> validator.validate(file("IMAGE/PNG; charset=binary", TestImages.png())))
                .doesNotThrowAnyException();
    }

    @Test
    void 파일이_없으면_missing_document() {
        assertRejectedWith(null, ErrorCode.MISSING_DOCUMENT);
        assertRejectedWith(file("image/png", new byte[0]), ErrorCode.MISSING_DOCUMENT);
    }

    @Test
    void 허용되지_않은_형식이면_unsupported_media_type() {
        assertRejectedWith(file("application/pdf", TestImages.png()), ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertRejectedWith(file(null, TestImages.png()), ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void 헤더는_이미지지만_실제로_디코드되지_않으면_invalid_image() {
        byte[] notAnImage = "이건 이미지가 아닙니다".getBytes(StandardCharsets.UTF_8);

        assertRejectedWith(file("image/png", notAnImage), ErrorCode.INVALID_IMAGE);
    }

    @Test
    void 형식_검사가_decode_검사보다_먼저다() {
        byte[] notAnImage = "not an image".getBytes(StandardCharsets.UTF_8);

        // 둘 다 위반이지만 문서 순서상 415가 먼저 나와야 한다
        assertRejectedWith(file("text/plain", notAnImage), ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void HEIC는_컨테이너_헤더로_통과시킨다() {
        // ImageIO가 HEIC를 디코드하지 못하므로 ftyp 브랜드만 확인한다
        assertThatCode(() -> validator.validate(
                file("image/heic", TestImages.heicHeader("heic"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(
                file("image/heif", TestImages.heicHeader("mif1"))))
                .doesNotThrowAnyException();
    }

    @Test
    void major_brand가_아니어도_compatible_brands에_있으면_통과한다() {
        assertThatCode(() -> validator.validate(
                file("image/heic", TestImages.heicHeader("mif1", "miaf", "heic"))))
                .doesNotThrowAnyException();
    }

    @Test
    void HEIC라고_했지만_다른_컨테이너면_invalid_image() {
        // ftyp는 있지만 mp4 브랜드
        assertRejectedWith(file("image/heic", TestImages.heicHeader("isom", "iso2", "mp41")),
                ErrorCode.INVALID_IMAGE);
        // ftyp 자체가 없음
        assertRejectedWith(file("image/heic", TestImages.png()), ErrorCode.INVALID_IMAGE);
        // 헤더가 잘림
        assertRejectedWith(file("image/heic", new byte[] {0, 0, 0, 1, 'f'}),
                ErrorCode.INVALID_IMAGE);
    }

    @Test
    void HEIC_바이트라도_png라고_주장하면_디코드_검사에_걸린다() {
        assertRejectedWith(file("image/png", TestImages.heicHeader("heic")),
                ErrorCode.INVALID_IMAGE);
    }

    @Test
    void 오류_코드는_문서에_정의된_상태값을_가진다() {
        assertThat(ErrorCode.MISSING_DOCUMENT.getCode()).isEqualTo("missing_document");
        assertThat(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode()).isEqualTo("unsupported_media_type");
        assertThat(ErrorCode.INVALID_IMAGE.getCode()).isEqualTo("invalid_image");
    }
}
