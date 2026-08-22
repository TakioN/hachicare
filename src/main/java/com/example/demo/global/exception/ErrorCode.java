package com.example.demo.global.exception;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ErrorCode {
    // 처방전 분석 작업 생성
    MISSING_DOCUMENT(HttpStatus.BAD_REQUEST, "missing_document", "처방전 이미지가 없습니다."),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "file_too_large", "허용 크기를 초과했습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", "지원하지 않는 형식입니다."),
    INVALID_IMAGE(HttpStatus.UNPROCESSABLE_CONTENT, "invalid_image", "이미지를 읽을 수 없습니다."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded", "요청 제한을 초과했습니다."),

    // 처방전 분석 상태 조회
    FORBIDDEN(HttpStatus.FORBIDDEN, "forbidden", "접근 권한이 없습니다."),
    EXTRACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "extraction_not_found", "존재하지 않는 작업입니다."),

    // 인증
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "unauthorized", "로그인이 필요합니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "token_expired", "인증이 만료되었습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "invalid_token", "유효하지 않은 인증입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "invalid_credentials", "이메일 또는 비밀번호가 올바르지 않습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "email_already_exists", "이미 가입된 이메일입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "user_not_found", "존재하지 않는 사용자입니다."),

    // 복약 관리
    MEDICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "medication_not_found", "존재하지 않는 약입니다."),
    EMPTY_MEDICATIONS(HttpStatus.BAD_REQUEST, "empty_medications", "등록할 약이 없습니다."),
    INVALID_MEDICATION(HttpStatus.BAD_REQUEST, "invalid_medication", "약 정보가 올바르지 않습니다."),
    EXTRACTION_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "extraction_already_confirmed",
            "이미 등록한 분석 결과입니다."),

    // 공통
    FILE_STORAGE_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "file_storage_error", "파일 저장에 실패했습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "서버 내부 오류가 발생했습니다."),

    TESTCODE(HttpStatus.BAD_REQUEST, "test_error", "Test Error");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
