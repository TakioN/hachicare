package com.example.demo.domain.prescription.analyzer;

import com.example.demo.domain.prescription.dto.result.PrescriptionExtractionResult;

/**
 * 처방전 분석 업스트림에 대한 포트.
 *
 * <p>구현체는 업스트림 원본 응답을 우리 타입으로 정규화해서 돌려준다. 원본을 그대로
 * 흘려보내지 않는다. 실패는 {@link AnalysisFailedException}으로 던져 실패 코드를 명시한다.
 */
public interface PrescriptionAnalyzer {

    /**
     * @param imageKey 오브젝트 스토리지에 있는 원본 이미지 키
     */
    PrescriptionExtractionResult analyze(String imageKey);
}
