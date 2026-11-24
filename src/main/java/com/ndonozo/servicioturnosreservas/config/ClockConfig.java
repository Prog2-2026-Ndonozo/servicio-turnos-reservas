package com.ndonozo.servicioturnosreservas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {
    @Bean
    Clock businessClock(@Value("${app.business-time-zone}") ZoneId zone){
        return Clock.system(zone);
    }
}
