package com.example.demo.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.demo.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class ApiResponseSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 성공_응답은_data로_감싼다() throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(ApiResponse.of("Connected..."));

        assertThat(json).isEqualTo("{\"data\":\"Connected...\"}");
    }

    @Test
    void 본문이_없는_성공_응답은_data가_null이다() throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(ApiResponse.empty());

        assertThat(json).isEqualTo("{\"data\":null}");
    }

    @Test
    void 오류_응답은_error_code_message를_담는다() throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(
                ApiErrorResponse.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE));

        assertThat(json).isEqualTo(
                "{\"error\":{\"code\":\"unsupported_media_type\",\"message\":\"지원하지 않는 형식입니다.\"}}");
    }

    @Test
    void 문서에_정의된_오류_코드와_상태값이_일치한다() {
        assertThat(ErrorCode.MISSING_DOCUMENT.getStatus().value()).isEqualTo(400);
        assertThat(ErrorCode.FILE_TOO_LARGE.getStatus().value()).isEqualTo(413);
        assertThat(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getStatus().value()).isEqualTo(415);
        assertThat(ErrorCode.INVALID_IMAGE.getStatus().value()).isEqualTo(422);
        assertThat(ErrorCode.RATE_LIMIT_EXCEEDED.getStatus().value()).isEqualTo(429);
        assertThat(ErrorCode.FORBIDDEN.getStatus().value()).isEqualTo(403);
        assertThat(ErrorCode.EXTRACTION_NOT_FOUND.getStatus().value()).isEqualTo(404);
    }
}
