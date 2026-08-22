package com.example.demo.domain.prescription.controller;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.domain.prescription.dto.ExtractionJobResponse;
import com.example.demo.domain.prescription.service.PrescriptionExtractionService;
import com.example.demo.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 인증이 필요한 경로다. 미인증 요청은 필터에서 401로 끝나므로 여기까지 오지 않는다.
 * principal은 사용자 공개 식별자이고, 그대로 작업의 소유자가 된다.
 */
@RestController
@RequestMapping("/api/v1/prescription-extractions")
@RequiredArgsConstructor
public class PrescriptionExtractionController {

    private final PrescriptionExtractionService extractionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ExtractionJobResponse>> create(
            @RequestPart("document") MultipartFile document,
            @AuthenticationPrincipal String userPublicId) {

        ExtractionJobResponse job = extractionService.create(document, userPublicId);

        return ResponseEntity
                .accepted()
                .location(URI.create("/api/v1/prescription-extractions/" + job.id()))
                .body(ApiResponse.of(job));
    }

    @GetMapping("/{extractionId}")
    public ApiResponse<ExtractionJobResponse> get(
            @PathVariable("extractionId") String extractionId,
            @AuthenticationPrincipal String userPublicId) {

        return ApiResponse.of(extractionService.find(extractionId, userPublicId));
    }
}
