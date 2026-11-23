package com.javmotion.api.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Background threads that wait on the worker. Bounded: when the queue is full new runs are
 * rejected instead of exhausting memory.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String EXECUTION_EXECUTOR = "executionExecutor";

    @Bean(EXECUTION_EXECUTOR)
    Executor executionExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("exec-dispatch-");
        ex.initialize();
        return ex;
    }
}
