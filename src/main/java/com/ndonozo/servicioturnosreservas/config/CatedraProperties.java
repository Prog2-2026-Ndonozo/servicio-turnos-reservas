package com.ndonozo.servicioturnosreservas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix= "app.catedra")
public record CatedraProperties(
        String baseUrl,
        String jwt,
        Duration connectTimeout,
        Duration readTimeout
) {
    @Override
    public String toString(){
        return "CatedraProperties[baseUrl=" + baseUrl + "]";
    }
}
