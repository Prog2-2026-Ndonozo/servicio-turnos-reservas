package com.ndonozo.servicioturnosreservas.reservation.infrastructure.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/** Reintentos limitados y observables (I §18.4), con los mismos valores que el listener del catálogo (ADR-0030). */
@Slf4j
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(5);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, e) -> log.error("Evento descartado tras agotar los reintentos (partition={}, offset={}, key={})",
                        record.partition(), record.offset(), record.key(), e),
                backOff);
        handler.setRetryListeners((record, e, attempt) -> log.warn("Falló un evento (offset={}, intento={}): {}",
                record.offset(), attempt, e == null ? "sin detalle" : (e.getCause() != null ? e.getCause() : e).toString()));
        return handler;
    }
}
