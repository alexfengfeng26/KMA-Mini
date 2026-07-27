package com.kma.knowledge.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 独立 Worker 进程使用非守护调度线程，确保无 Web 模式持续运行并支持优雅关闭。 */
@Configuration
@ConditionalOnProperty(prefix = "knowledge.ingestion", name = "worker-enabled", havingValue = "true")
public class KnowledgeWorkerSchedulingConfig {

    @Bean
    TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("kma-worker-schedule-");
        scheduler.setDaemon(false);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
