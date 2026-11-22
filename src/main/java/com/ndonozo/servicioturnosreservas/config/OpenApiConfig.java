package com.ndonozo.servicioturnosreservas.config;



import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA = "bearer-jwt";

    @Bean
    public OpenAPI turnosOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Servicio de turnos y reservas")
                        .version("v1")
                        .description("Backend de la app KMP: registro e inicio de sesión del usuario, búsqueda de "
                                + "profesionales y categorías (que reenvía al servicio de catálogo) y, más adelante, "
                                + "disponibilidad y reservas. Las rutas protegidas exigen el JWT que devuelve "
                                + "/api/authenticate en el campo id_token: botón \"Authorize\"."))
                .components(new Components().addSecuritySchemes(ESQUEMA, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }
}