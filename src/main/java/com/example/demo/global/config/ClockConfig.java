package com.example.demo.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /**
     * 시각 생성을 주입 가능하게 두어 테스트에서 고정할 수 있게 한다.
     *
     * <p>UTC가 아니라 서비스 시간대로 둔다. Instant는 시간대와 무관하지만 "오늘"을 구할 때
     * 달라진다. UTC로 두면 한국 새벽 0~9시에 서버가 어제 날짜를 쓴다.
     */
    @Bean
    public Clock clock(@Value("${app.timezone:Asia/Seoul}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
