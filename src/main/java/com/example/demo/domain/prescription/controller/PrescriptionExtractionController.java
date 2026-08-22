package com.example.demo.domain.prescription.controller;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.domain.prescription.dto.ExtractionJobResponse;
import com.example.demo.domain.prescription.service.PrescriptionExtractionService;
import com.example.demo.global.response.ApiResponse;
import com.example.demo.global.session.AnonymousSessionManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/prescription-extractions")
@RequiredArgsConstructor
public class PrescriptionExtractionController {

    private final PrescriptionExtractionService extractionService;
    private final AnonymousSessionManager sessionManager;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ExtractionJobResponse>> create(
            @RequestPart("document") MultipartFile document,
            HttpServletRequest request,
            HttpServletResponse response) {

        String ownerKey = sessionManager.resolveOrIssue(request, response);
        ExtractionJobResponse job = extractionService.create(document, ownerKey);

        return ResponseEntity
                .accepted()
                .location(URI.create("/api/v1/prescription-extractions/" + job.id()))
                .body(ApiResponse.of(job));
    }
}
