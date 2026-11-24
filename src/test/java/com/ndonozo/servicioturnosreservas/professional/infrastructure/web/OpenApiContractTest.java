package com.ndonozo.servicioturnosreservas.professional.infrastructure.web;

import com.ndonozo.servicioturnosreservas.support.TestRsaKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El contrato hacia KMP tal como lo publica Swagger en el perfil dev: rutas, parámetros, respuestas y esquema bearer. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers
@TestPropertySource(properties = "app.security.jwt.secret=" + OpenApiContractTest.SECRETO)
class OpenApiContractTest {

    static final String SECRETO =
            "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBw==";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void claveDelCatalogo(DynamicPropertyRegistry registry) {
        registry.add("app.catedra.base-url", () -> "http://localhost:1");
        registry.add("app.catedra.jwt", () -> "jwt-de-prueba");
        registry.add("app.catalog.jwt.private-key-file", () -> TestRsaKeys.privateKeyPemFile().toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void la_documentacion_es_publica_en_dev_y_declara_el_esquema_bearer() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Servicio de turnos y reservas"))
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0]['bearer-jwt']").exists());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void la_busqueda_documenta_sus_cuatro_filtros_opcionales() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/professionals/search'].get.parameters[*].name")
                        .value(containsInAnyOrder("categoryId", "name", "enabled", "availableOn")))
                .andExpect(jsonPath("$.paths['/api/professionals/search'].get.parameters[?(@.required==true)]")
                        .value(empty()));
    }

    @Test
    void las_dos_operaciones_documentan_sus_respuestas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/professionals/search'].get.responses.keys()")
                        .value(hasItems("200", "400", "401", "502", "503")))
                .andExpect(jsonPath("$.paths['/api/professional-categories'].get.responses.keys()")
                        .value(hasItems("200", "401", "502", "503")));
    }

    @Test
    void registro_y_login_aparecen_sin_candado_porque_son_publicos() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/register'].post.security").value(empty()))
                .andExpect(jsonPath("$.paths['/api/authenticate'].post.security").value(empty()))
                .andExpect(jsonPath("$.paths['/api/professionals/search'].get.security").doesNotExist());
    }

    @Test
    void la_disponibilidad_documenta_sus_tres_parametros_obligatorios_y_sus_respuestas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/availability'].get.parameters[*].name")
                        .value(containsInAnyOrder("professionalId", "from", "to")))
                .andExpect(jsonPath("$.paths['/api/availability'].get.parameters[?(@.required==true)].name")
                        .value(containsInAnyOrder("professionalId", "from", "to")))
                .andExpect(jsonPath("$.paths['/api/availability'].get.responses.keys()")
                        .value(containsInAnyOrder("200", "400", "401", "404", "502", "503")))
                .andExpect(jsonPath("$.paths['/api/availability'].get.security").doesNotExist());
    }

    @Test
    void la_reserva_documenta_su_cuerpo_sus_respuestas_y_no_expone_el_token() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/reservations'].post.requestBody.required").value(true))
                .andExpect(jsonPath("$.components.schemas.CreateReservationRequest.required")
                        .value(containsInAnyOrder("professionalId", "date", "startTime")))
                .andExpect(jsonPath("$.components.schemas.ReservationResponse.properties.reservationProcessId").exists())
                .andExpect(jsonPath("$.components.schemas.ReservationResponse.properties.holdId").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/reservations'].post.responses.keys()")
                        .value(containsInAnyOrder("201", "400", "401", "404", "409", "502", "503")))
                .andExpect(jsonPath("$.paths['/api/reservations'].post.parameters").doesNotExist());
    }
}
