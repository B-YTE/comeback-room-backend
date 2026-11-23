package com.comebackroom.domain.watchingroom.presence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;

@Configuration
public class PresenceSchedulerConfig {

    private static final int POOL_SIZE = 2;

    @Bean(name = "presenceTaskScheduler")
    public ThreadPoolTaskScheduler presenceTaskScheduler(Clock clock) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix("presence-grace-");
        scheduler.setClock(clock);
        return scheduler;
    }
}
