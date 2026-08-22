package com.example.demo.domain.medication.dto;

import java.util.List;

/**
 * 목록을 한 번에 등록한다.
 *
 * <p>분석 결과에서 넘어온 경우 sourceExtractionId로 출처를 남긴다. 서버가 result_json을
 * 다시 읽지 않고 클라이언트가 보낸 값을 그대로 쓰는 이유는, 사용자가 화면에서 고친 값이
 * 최종이기 때문이다. 손으로 추가하는 경우에는 sourceExtractionId 없이 보낸다.
 */
public record MedicationBulkCreateRequest(
    String sourceExtractionId,
    List<MedicationRequest> medications
) {
}
