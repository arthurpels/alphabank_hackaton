package com.alphabank.backend.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.alphabank.backend.util.DemoClock;

@Configuration
public class ClockConfig {

    @Bean
    public DemoClock clock() {
        return new DemoClock(Clock.systemDefaultZone());
    }
}
