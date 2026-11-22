package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.config;

import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.client.CatalogRestClient;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.token.CatalogPrivateKeyReader;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.token.CatalogTokenIssuer;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.token.CatalogTokenProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
public class CatalogRestConfig {
    @Bean
    CatalogTokenIssuer catalogTokenIssuer(CatalogProperties properties){
        return new CatalogTokenIssuer(
                CatalogPrivateKeyReader.read(properties.jwt().privateKeyFile()),
                properties.jwt().issuer(),
                properties.jwt().audience(),
                Clock.systemUTC());

    }
    @Bean
    CatalogTokenProvider catalogTokenProvider(CatalogTokenIssuer issuer) {
        return new CatalogTokenProvider(issuer, Clock.systemUTC());
    }
    @Bean
    CatalogRestClient catalogRestClient(CatalogProperties properties, CatalogTokenProvider tokens) {
        return new CatalogRestClient(
                restClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout()), tokens);
    }
    public static RestClient restClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

}
