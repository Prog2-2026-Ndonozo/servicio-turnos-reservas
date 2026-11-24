package com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraIntegrationException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.HoldRequest;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.HoldResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El cliente del hold contra un servidor HTTP real (WireMock): pedido, mapeo, rechazos de negocio y traducción de errores. */
class CatedraHoldRestClientWireMockTest {

    private static final String RUTA = "/api/appointment-holds";
    private static final HoldRequest PEDIDO = new HoldRequest(15, "2026-09-14", "09:30", "user-42");

    private WireMockServer catedra;

    @BeforeEach
    void arrancaLaCatedraSimulada() {
        catedra = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        catedra.start();
    }

    @AfterEach
    void apagaLaCatedraSimulada() {
        catedra.stop();
    }

    private CatedraHoldRestClient client(Duration lectura) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(lectura);
        return new CatedraHoldRestClient(RestClient.builder().baseUrl("http://localhost:" + catedra.port())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer jwt-tecnico").requestFactory(factory).build());
    }

    private void responde(int estado, String cuerpo) {
        catedra.stubFor(post(urlPathEqualTo(RUTA)).willReturn(aResponse().withStatus(estado)
                .withHeader("Content-Type", estado >= 400 ? "application/problem+json" : "application/json")
                .withBody(cuerpo)));
    }

    private void rechazaCon(int estado, String code) {
        responde(estado, "{\"title\":\"x\",\"status\":" + estado + ",\"code\":\"" + code + "\"}");
    }

    @Test
    void envia_el_cuerpo_del_contrato_con_la_hora_como_HH_mm_y_el_jwt_tecnico() {
        responde(201, """
                {"holdId":"hold-6c0c2f41","reservationProcessId":"process-a92b47d0","status":"HELD","expiresAt":"2026-09-10T14:44:50Z"}""");

        client(Duration.ofSeconds(2)).createHold(PEDIDO);

        catedra.verify(1, postRequestedFor(urlPathEqualTo(RUTA))
                .withHeader("Authorization", equalTo("Bearer jwt-tecnico"))
                .withHeader("Content-Type", com.github.tomakehurst.wiremock.client.WireMock.containing("application/json"))
                .withRequestBody(equalToJson("""
                        {"professionalId":15,"date":"2026-09-14","startTime":"09:30","externalPatientId":"user-42"}""")));
    }

    @Test
    void mapea_el_hold_e_ignora_campos_desconocidos() {
        responde(201, """
                {"holdId":"hold-1","reservationProcessId":"process-1","status":"HELD","expiresAt":"2026-09-10T14:44:50Z","nuevo":1}""");

        HoldResponse hold = client(Duration.ofSeconds(2)).createHold(PEDIDO);

        assertThat(hold.holdId()).isEqualTo("hold-1");
        assertThat(hold.reservationProcessId()).isEqualTo("process-1");
        assertThat(hold.expiresAt()).isEqualTo(Instant.parse("2026-09-10T14:44:50Z"));
    }

    @Test
    void los_cinco_rechazos_de_negocio_salen_con_su_code() {
        for (var caso : new Object[][]{{409, "SLOT_ALREADY_HELD"}, {409, "SLOT_ALREADY_RESERVED"}, {400, "INVALID_SLOT"},
                {400, "PROFESSIONAL_DISABLED"}, {404, "PROFESSIONAL_NOT_FOUND"}}) {
            rechazaCon((int) caso[0], (String) caso[1]);

            assertThatThrownBy(() -> client(Duration.ofSeconds(2)).createHold(PEDIDO))
                    .isInstanceOfSatisfying(CatedraHoldRejectedException.class,
                            e -> assertThat(e.getCode()).isEqualTo(caso[1]));
        }
    }

    @Test
    void un_error_de_validacion_de_la_catedra_es_de_integracion_porque_el_pedido_ya_se_valido() {
        rechazaCon(400, "VALIDATION_ERROR");

        assertThatThrownBy(() -> client(Duration.ofSeconds(2)).createHold(PEDIDO))
                .isInstanceOf(CatedraIntegrationException.class)
                .hasMessageContaining("400").hasMessageContaining("VALIDATION_ERROR");
    }

    @Test
    void un_401_o_un_403_son_de_integracion_y_no_se_reintentan() {
        for (int estado : new int[]{401, 403}) {
            catedra.resetAll();
            responde(estado, "{\"title\":\"x\"}");

            assertThatThrownBy(() -> client(Duration.ofSeconds(2)).createHold(PEDIDO))
                    .isInstanceOf(CatedraIntegrationException.class).hasMessageContaining(String.valueOf(estado));
            catedra.verify(1, postRequestedFor(urlPathEqualTo(RUTA)));
        }
    }

    @Test
    void un_4xx_sin_code_conocido_es_de_integracion() {
        rechazaCon(409, "CODIGO_NUEVO");
        assertThatThrownBy(() -> client(Duration.ofSeconds(2)).createHold(PEDIDO))
                .isInstanceOf(CatedraIntegrationException.class);

        responde(409, "esto no es json");
        assertThatThrownBy(() -> client(Duration.ofSeconds(2)).createHold(PEDIDO))
                .isInstanceOf(CatedraIntegrationException.class);
    }

    @Test
    void los_5xx_son_catedra_no_disponible_y_el_POST_no_se_repite() {
        responde(503, "{}");

        assertThatThrownBy(() -> client(Duration.ofSeconds(2)).createHold(PEDIDO))
                .isInstanceOf(CatedraUnavailableException.class);

        // un timeout no prueba que el hold no se creó: repetir el POST podría crear otro (I §18.4)
        catedra.verify(1, postRequestedFor(urlPathEqualTo(RUTA)));
    }

    @Test
    void una_catedra_demasiado_lenta_es_no_disponible_y_el_POST_no_se_repite() {
        catedra.stubFor(post(urlPathEqualTo(RUTA)).willReturn(aResponse().withStatus(201).withFixedDelay(1500)
                .withHeader("Content-Type", "application/json").withBody("{}")));

        assertThatThrownBy(() -> client(Duration.ofMillis(300)).createHold(PEDIDO))
                .isInstanceOf(CatedraUnavailableException.class);

        catedra.verify(1, postRequestedFor(urlPathEqualTo(RUTA)));
    }

    @Test
    void una_catedra_apagada_es_no_disponible() {
        CatedraHoldRestClient client = client(Duration.ofSeconds(2));
        catedra.stop();

        assertThatThrownBy(() -> client.createHold(PEDIDO)).isInstanceOf(CatedraUnavailableException.class);
    }

    @Test
    void un_hold_incompleto_o_en_otro_estado_es_de_integracion() {
        for (String cuerpo : new String[]{
                "{\"reservationProcessId\":\"p\",\"status\":\"HELD\",\"expiresAt\":\"2026-09-10T14:44:50Z\"}",
                "{\"holdId\":\"h\",\"status\":\"HELD\",\"expiresAt\":\"2026-09-10T14:44:50Z\"}",
                "{\"holdId\":\"h\",\"reservationProcessId\":\"p\",\"status\":\"HELD\"}",
                "{\"holdId\":\"h\",\"reservationProcessId\":\"p\",\"status\":\"CONFIRMED\",\"expiresAt\":\"2026-09-10T14:44:50Z\"}",
                "{}"}) {
            responde(201, cuerpo);

            assertThatThrownBy(() -> client(Duration.ofSeconds(2)).createHold(PEDIDO))
                    .isInstanceOf(CatedraIntegrationException.class);
        }
    }

    @Test
    void un_cuerpo_ilegible_es_de_integracion() {
        responde(201, "no es json");

        assertThatThrownBy(() -> client(Duration.ofSeconds(2)).createHold(PEDIDO))
                .isInstanceOf(CatedraIntegrationException.class);
    }
}
