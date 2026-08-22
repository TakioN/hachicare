package com.example.demo.domain.prescription.analyzer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Upstage 연동(7단계) 전까지 쓰는 임시 구현.
 *
 * <p>프런트가 폴링과 결과 렌더링을 먼저 붙여볼 수 있도록 문서의 결과 스키마를 그대로 흉내낸다.
 * {@code app.analyzer=upstage}로 바꾸면 실제 구현으로 교체된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.analyzer", havingValue = "stub", matchIfMissing = true)
public class StubPrescriptionAnalyzer implements PrescriptionAnalyzer {

    private static final String CANNED_RESULT = """
            {
              "reviewStatus": "needs_review",
              "documentType": "patient_copy_prescription",
              "medications": [
                {
                  "id": "med_1",
                  "drugName": {
                    "value": "아모잘탄정 5/50mg",
                    "status": "extracted",
                    "confidence": "high"
                  },
                  "dose": {
                    "value": { "value": 1, "unit": "정", "rawText": "1정" },
                    "status": "extracted",
                    "confidence": "high"
                  },
                  "frequencyPerDay": { "value": 1, "status": "extracted", "confidence": "high" },
                  "durationDays": { "value": 30, "status": "extracted", "confidence": "low" },
                  "timingInstruction": {
                    "source": { "value": "아침 식후 30분", "status": "extracted", "confidence": "high" },
                    "english": "30 minutes after breakfast"
                  }
                }
              ],
              "issues": [
                {
                  "code": "low_confidence",
                  "medicationId": "med_1",
                  "field": "durationDays",
                  "message": "투약 일수를 확인해 주세요."
                }
              ]
            }""";

    @Override
    public String analyze(String imageKey) {
        log.warn("스텁 분석기가 동작 중이다. 실제 분석이 아니다: imageKey={}", imageKey);
        return CANNED_RESULT;
    }
}
