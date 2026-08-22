package com.example.demo.domain.prescription.event;

/**
 * 분석 작업이 생성되었음을 알린다.
 *
 * <p>엔티티가 아니라 식별자만 싣는다. 리스너는 커밋 이후 다른 스레드/트랜잭션에서 돌기 때문에
 * 영속성 컨텍스트가 끊긴 엔티티를 넘기면 안 된다.
 */
public record ExtractionRequestedEvent(
    String publicId
) {
}
