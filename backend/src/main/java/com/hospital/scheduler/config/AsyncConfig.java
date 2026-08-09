package com.hospital.scheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Async (@Async) TaskExecutor.
 *
 * Spring creates a number of TaskExecutor beans implicitly (notably
 * the WebSocket STOMP simple broker, which registers
 * clientInboundChannelExecutor, clientOutboundChannelExecutor,
 * brokerChannelExecutor and messageBrokerTaskScheduler). Without a
 * dedicated bean the @Async infrastructure picks one at random and
 * logs:
 *   "More than one TaskExecutor bean found within the context, and
 *    none is named 'taskExecutor'. Mark one of them as primary or
 *    name it 'taskExecutor'."
 *
 * Defining this bean as @Primary AND naming it 'taskExecutor' makes
 * @Async deterministic and routes work away from the STOMP pool so a
 * slow email send can never block a WebSocket client frame.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    @Primary
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("app-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}