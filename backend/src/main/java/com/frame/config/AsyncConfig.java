package com.frame.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Deliberately bounded executor for video processing jobs. Spring's default {@code @Async}
 * executor ({@code SimpleAsyncTaskExecutor}) spawns an unbounded number of threads, which is fine
 * for occasional fire-and-forget calls but dangerous for something as heavy as video processing
 * under concurrent uploads. This pool caps concurrency and queues the rest instead of exhausting
 * memory/threads — a deliberate trade-off (bounded latency under burst load vs. bounded resource
 * usage) worth being able to explain.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "processingExecutor")
    public ThreadPoolTaskExecutor processingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("frame-processing-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
