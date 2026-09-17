package com.example.tomatomall.configure;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
@Configuration
public class OrderSchedulingConfig {
    @Bean public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("order-jobs-");return scheduler;
    }
}
