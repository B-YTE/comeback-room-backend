package com.comebackroom.global.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

@Configuration
public class TimeZoneConfig {

    public static final ZoneId APPLICATION_ZONE_ID = ZoneId.of("Asia/Seoul");

    @PostConstruct
    void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(APPLICATION_ZONE_ID));
    }

    @Bean
    public Clock clock() {
        return Clock.system(APPLICATION_ZONE_ID);
    }
}
