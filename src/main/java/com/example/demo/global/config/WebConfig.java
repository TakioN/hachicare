package com.example.demo.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.demo.global.ratelimit.RateLimitInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry){
        registry.addMapping("/**")
        .allowedOrigins("http://localhost:3000", "https://junctionx.shj.rip")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
        .allowedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 분석 생성만 막는다. 상태 조회(/{id})는 폴링이 정상 동작이라 대상이 아니다.
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/v1/prescription-extractions");
    }
}
