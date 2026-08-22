package com.example.demo.domain.prescription.analyzer.upstage;

import java.net.http.HttpClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;

@Configuration
@ConditionalOnProperty(name = "app.analyzer", havingValue = "upstage")
@RequiredArgsConstructor
public class UpstageClientConfig {

    private final UpstageProperties properties;

    @Bean
    public RestClient upstageRestClient() {
        return decorate(RestClient.builder())
                .requestFactory(timeoutBoundFactory())
                .build();
    }

    /**
     * 주소와 인증만 얹는다. 요청 팩토리는 건드리지 않으므로 테스트가 모의 서버를 물릴 수 있고,
     * 덕분에 baseUrl과 인증 헤더는 테스트에서 재현하지 않고 이 코드 그대로 검증된다.
     */
    RestClient.Builder decorate(RestClient.Builder builder) {
        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey());
    }

    /** 업스트림이 응답하지 않을 때 워커 스레드를 무한정 붙들지 않도록 양쪽 타임아웃을 건다. */
    private JdkClientHttpRequestFactory timeoutBoundFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.requestTimeout()).build());
        factory.setReadTimeout(properties.requestTimeout());
        return factory;
    }
}
