package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;



import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix= "app.catalog")
public record CatalogProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Jwt jwt
        ) {
    public record Jwt(
            Path privateKeyFile,
            String issuer,
            String audience
            ){

    }

}
