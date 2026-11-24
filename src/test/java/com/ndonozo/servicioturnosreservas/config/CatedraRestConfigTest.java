package com.ndonozo.servicioturnosreservas.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La configuración compartida de la cátedra (ADR-0007): URL base, JWT técnico en cada pedido y el JWT nunca a la vista. */
class CatedraRestConfigTest {

    private static final String JWT = "jwt-secreto-de-prueba";

    private WireMockServer catedra;

    @BeforeEach
    void arranca() {
        catedra = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        catedra.start();
    }

    @AfterEach
    void apaga() {
        catedra.stop();
    }

    private CatedraProperties propiedades(String jwt) {
        return new CatedraProperties("http://localhost:" + catedra.port(), jwt, Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    @Test
    void el_cliente_manda_el_jwt_tecnico_en_cada_pedido() {
        catedra.stubFor(get(urlPathEqualTo("/algo")).willReturn(aResponse().withStatus(200).withBody("ok")));
        RestClient client = new CatedraRestConfig().catedraHttpClient(propiedades(JWT));

        client.get().uri("/algo").retrieve().body(String.class);
        client.get().uri("/algo").retrieve().body(String.class);

        catedra.verify(2, getRequestedFor(urlPathEqualTo("/algo")).withHeader("Authorization", equalTo("Bearer " + JWT)));
    }

    @Test
    void sin_jwt_la_aplicacion_no_arranca_y_el_mensaje_no_lleva_secretos() {
        for (String vacio : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> new CatedraRestConfig().catedraHttpClient(propiedades(vacio)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CATEDRA_JWT");
        }
    }

    @Test
    void el_toString_de_las_propiedades_no_muestra_el_jwt() {
        assertThat(propiedades(JWT).toString()).doesNotContain(JWT).contains("baseUrl");
    }
}
