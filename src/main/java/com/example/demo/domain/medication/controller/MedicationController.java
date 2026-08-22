package com.example.demo.domain.medication.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.domain.medication.dto.MedicationBulkCreateRequest;
import com.example.demo.domain.medication.dto.MedicationFinishRequest;
import com.example.demo.domain.medication.dto.MedicationRequest;
import com.example.demo.domain.medication.dto.MedicationResponse;
import com.example.demo.domain.medication.entity.MedicationStatus;
import com.example.demo.domain.medication.service.MedicationService;
import com.example.demo.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 인증이 필요하다. principal이 곧 소유자다. */
@RestController
@RequestMapping("/api/v1/medications")
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationService medicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<List<MedicationResponse>> createAll(
            @RequestBody MedicationBulkCreateRequest request,
            @AuthenticationPrincipal String ownerKey) {

        return ApiResponse.of(medicationService.createAll(request, ownerKey));
    }

    /** status를 주지 않으면 종료한 약까지 전부 돌려준다. */
    @GetMapping
    public ApiResponse<List<MedicationResponse>> findAll(
            @RequestParam(value = "status", required = false) MedicationStatus status,
            @AuthenticationPrincipal String ownerKey) {

        return ApiResponse.of(medicationService.findAll(ownerKey, status));
    }

    /** 부분 수정이 아니라 전체 교체다. 보내지 않은 항목은 비워진다. */
    @PutMapping("/{medicationId}")
    public ApiResponse<MedicationResponse> update(
            @PathVariable("medicationId") String medicationId,
            @RequestBody MedicationRequest request,
            @AuthenticationPrincipal String ownerKey) {

        return ApiResponse.of(medicationService.update(medicationId, request, ownerKey));
    }

    /** 복용 종료. 기록은 남는다. endedOn을 주지 않으면 오늘로 잡는다. */
    @PostMapping("/{medicationId}/finish")
    public ApiResponse<MedicationResponse> finish(
            @PathVariable("medicationId") String medicationId,
            @RequestBody(required = false) MedicationFinishRequest request,
            @AuthenticationPrincipal String ownerKey) {

        return ApiResponse.of(medicationService.finish(
                medicationId, request == null ? null : request.endedOn(), ownerKey));
    }

    @DeleteMapping("/{medicationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable("medicationId") String medicationId,
            @AuthenticationPrincipal String ownerKey) {

        medicationService.delete(medicationId, ownerKey);
    }
}
