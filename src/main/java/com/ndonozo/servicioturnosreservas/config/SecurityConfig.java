package com.ndonozo.servicioturnosreservas.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] RUTAS_PUBLICAS = {"/api/register", "/api/authenticate"};

    private final ProblemJsonSecurityHandlers handlers;

    /**
     * Valida el JWT del usuario: firma HS512 con el mismo JWT_SECRET con el que lo emite JwtTokenIssuer y vigencia sin
     * tolerancia de reloj (quien emite y quien valida son el mismo servicio). Fijar el algoritmo rechaza tokens con otro
     * (HS256, "none") y el JWT técnico RS256 que este servicio le manda al catálogo.
     */
    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.jwt.secret}") String secret) {
        SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA512");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS512).build();
        decoder.setJwtValidator(new JwtTimestampValidator(Duration.ZERO));
        return decoder;
    }

    // Solo en el perfil dev: la documentación de la API es pública.
    @Bean
    @Order(1)
    @Profile("dev")
    SecurityFilterChain docsFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        DefaultBearerTokenResolver bearerResolver = new DefaultBearerTokenResolver();
        return http
                .csrf(AbstractHttpConfigurer::disable) // la autenticación va en un header, no en una cookie
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, RUTAS_PUBLICAS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        // En las rutas públicas el header Authorization se ignora: un token vencido que la app
                        // siga mandando no debe impedir registrarse ni iniciar sesión.
                        .bearerTokenResolver(request -> esRutaPublica(request) ? null : bearerResolver.resolve(request))
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .build();
    }

    private static boolean esRutaPublica(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod()) && List.of(RUTAS_PUBLICAS).contains(request.getRequestURI());
    }
}