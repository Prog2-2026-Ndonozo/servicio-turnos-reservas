package com.ndonozo.servicioturnosreservas;

import com.ndonozo.servicioturnosreservas.user.application.service.UserService;
import com.ndonozo.servicioturnosreservas.user.domain.model.RegisterUserCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Humo: arranca la aplicacion completa contra un PostgreSQL real y recorre registro y login de punta a punta
 * (caso de uso -> adaptadores reales -> base). Comprueba que todos los puertos tienen su adaptador.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "app.security.jwt.secret=" + ServicioTurnosReservasApplicationTests.SECRETO_DE_PRUEBA)
class ServicioTurnosReservasApplicationTests {

    // 64 bytes en Base64, solo para pruebas
    static final String SECRETO_DE_PRUEBA =
            "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBw==";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void claveDelCatalogo(DynamicPropertyRegistry registry) {
        registry.add("app.catedra.base-url", () -> "http://localhost:1");
        registry.add("app.catedra.jwt", () -> "jwt-de-prueba");
        registry.add("app.catalog.jwt.private-key-file",
                () -> com.ndonozo.servicioturnosreservas.support.TestRsaKeys.privateKeyPemFile().toString());
    }

    @Autowired
    private UserService userService;

    @Test
    void la_aplicacion_arranca_y_permite_registrarse_e_iniciar_sesion() {
        assertThat(Base64.getDecoder().decode(SECRETO_DE_PRUEBA)).hasSize(64);

        userService.register(new RegisterUserCommand("Ana", "clave1234", "Ana", "Paz", "ana@correo.com", null, "es"));
        String token = userService.authenticate("ana", "clave1234").value();

        assertThat(token.split("\\.")).hasSize(3);
    }
}
