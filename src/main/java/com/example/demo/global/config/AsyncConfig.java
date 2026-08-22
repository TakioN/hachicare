package com.example.demo.global.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * 분석 작업 전용 풀. 큐가 차면 거절한다.
     *
     * <p>CallerRunsPolicy를 쓰면 커밋 직후 리스너가 요청 스레드에서 돌아 업로드 응답이 늦어진다.
     * 거절된 작업은 pending으로 남고 스위퍼가 타임아웃으로 정리하므로 유실되지 않는다.
     */
    @Bean
    public Executor extractionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("extraction-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
