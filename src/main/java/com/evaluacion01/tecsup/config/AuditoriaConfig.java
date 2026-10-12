package com.evaluacion01.tecsup.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AuditoriaConfig {

    @Bean
    public Clock auditoriaClock() {
        return Clock.systemUTC();
    }
}
