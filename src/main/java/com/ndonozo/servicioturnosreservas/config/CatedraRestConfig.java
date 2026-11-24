package com.ndonozo.servicioturnosreservas.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(CatedraProperties.class)
public class CatedraRestConfig {
    @Bean
    RestClient catedraHttpClient(CatedraProperties properties) {
        if (properties.jwt() == null || properties.jwt().isBlank()) {
            throw new IllegalStateException("Falta el JWT técnico de la cátedra: definí CATEDRA_JWT (app.catedra.jwt)");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.jwt())
                .requestFactory(requestFactory)
                .build();
    }
}
