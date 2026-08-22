package com.example.demo.global.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** 시각 생성을 주입 가능하게 두어 테스트에서 고정할 수 있게 한다. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
