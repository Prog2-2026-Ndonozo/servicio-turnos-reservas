package com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraIntegrationException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.ConfirmHoldRequest;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.ConfirmHoldResponse;
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
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Confirmar el hold contra un servidor HTTP real (WireMock): pedido, mapeo, rechazos de negocio y traducción de errores. */
class CatedraConfirmHoldRestClientWireMockTest {

    private static final String RUTA = "/api/appointment-holds/hold-1/confirm";
    private static final ConfirmHoldRequest PEDIDO = new ConfirmHoldRequest("user-42", "Juan", "Perez");
    private static final String OK = """
            {"reservationProcessId":"process-1","status":"WAITING_FOR_PHONE","expiresAt":"2026-09-10T14:44:50Z"}""";

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

    private ConfirmHoldResponse confirma() {
        return client(Duration.ofSeconds(2)).confirmHold("hold-1", PEDIDO);
    }

    @Test
    void envia_el_cuerpo_del_contrato_a_la_ruta_del_hold_con_el_jwt_tecnico() {
        responde(202, OK);

        confirma();

        catedra.verify(1, postRequestedFor(urlPathEqualTo(RUTA))
                .withHeader("Authorization", equalTo("Bearer jwt-tecnico"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(equalToJson("""
                        {"externalPatientId":"user-42","patientFirstName":"Juan","patientLastName":"Perez"}""")));
    }

    @Test
    void mapea_la_respuesta_e_ignora_campos_desconocidos() {
        responde(202, """
                {"reservationProcessId":"process-1","status":"WAITING_FOR_PHONE","expiresAt":"2026-09-10T14:44:50Z","nuevo":1}""");

        ConfirmHoldResponse respuesta = confirma();

        assertThat(respuesta.reservationProcessId()).isEqualTo("process-1");
        assertThat(respuesta.expiresAt()).isEqualTo(Instant.parse("2026-09-10T14:44:50Z"));
    }

    @Test
    void los_rechazos_de_negocio_salen_con_su_code() {
        for (var caso : new Object[][]{{404, "HOLD_EXPIRED"}, {404, "HOLD_NOT_FOUND"},
                {409, "RESERVATION_PROCESS_ALREADY_CONFIRMED"}, {409, "SLOT_ALREADY_RESERVED"}}) {
            rechazaCon((int) caso[0], (String) caso[1]);

            assertThatThrownBy(this::confirma).isInstanceOfSatisfying(CatedraHoldRejectedException.class,
                    e -> assertThat(e.getCode()).isEqualTo(caso[1]));
        }
    }

    @Test
    void un_paciente_o_un_dueno_distinto_es_de_integracion_porque_no_deberia_pasar() {
        for (var caso : new Object[][]{{400, "EXTERNAL_PATIENT_MISMATCH"}, {403, "HOLD_OWNER_MISMATCH"},
                {400, "VALIDATION_ERROR"}, {400, "INVALID_SLOT"}, {404, "PROFESSIONAL_NOT_FOUND"}}) {
            rechazaCon((int) caso[0], (String) caso[1]);

            assertThatThrownBy(this::confirma).isInstanceOf(CatedraIntegrationException.class)
                    .hasMessageContaining((String) caso[1]);
        }
    }

    @Test
    void un_401_es_de_integracion_y_no_se_reintenta() {
        responde(401, "{\"title\":\"x\"}");

        assertThatThrownBy(this::confirma).isInstanceOf(CatedraIntegrationException.class).hasMessageContaining("401");
        catedra.verify(1, postRequestedFor(urlPathEqualTo(RUTA)));
    }

    @Test
    void los_5xx_son_catedra_no_disponible_y_el_POST_no_se_repite() {
        responde(503, "{}");

        assertThatThrownBy(this::confirma).isInstanceOf(CatedraUnavailableException.class);

        catedra.verify(1, postRequestedFor(urlPathEqualTo(RUTA)));
    }

    @Test
    void una_catedra_demasiado_lenta_es_no_disponible_y_el_POST_no_se_repite() {
        catedra.stubFor(post(urlPathEqualTo(RUTA)).willReturn(aResponse().withStatus(202).withFixedDelay(1500)
                .withHeader("Content-Type", "application/json").withBody(OK)));

        assertThatThrownBy(() -> client(Duration.ofMillis(300)).confirmHold("hold-1", PEDIDO))
                .isInstanceOf(CatedraUnavailableException.class);

        catedra.verify(1, postRequestedFor(urlPathEqualTo(RUTA)));
    }

    @Test
    void una_respuesta_vacia_o_en_otro_estado_es_de_integracion() {
        for (String cuerpo : new String[]{"{}",
                "{\"reservationProcessId\":\"p\",\"status\":\"HELD\",\"expiresAt\":\"2026-09-10T14:44:50Z\"}",
                "no es json"}) {
            responde(202, cuerpo);

            assertThatThrownBy(this::confirma).isInstanceOf(CatedraIntegrationException.class);
        }
    }

    @Test
    void el_hold_id_va_codificado_en_la_ruta() {
        catedra.stubFor(post(urlPathEqualTo("/api/appointment-holds/hold%20raro/confirm")).willReturn(aResponse()
                .withStatus(202).withHeader("Content-Type", "application/json").withBody(OK)));

        client(Duration.ofSeconds(2)).confirmHold("hold raro", PEDIDO);

        catedra.verify(1, postRequestedFor(urlPathEqualTo("/api/appointment-holds/hold%20raro/confirm")));
    }
}
