package com.ndonozo.servicioturnosreservas.availability.infrastructure.catedrarest.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.ndonozo.servicioturnosreservas.availability.application.exception.CatedraIntegrationException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.availability.infrastructure.catedrarest.dto.OccupanciesResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El cliente de la cátedra contra un servidor HTTP real (WireMock): pedido, mapeo y traducción de errores. */
class CatedraRestClientWireMockTest {

    private static final String RUTA = "/api/appointment-occupancies";
    private static final LocalDate DESDE = LocalDate.of(2026, 9, 14);
    private static final LocalDate HASTA = LocalDate.of(2026, 9, 20);

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

    private CatedraRestClient client(Duration lectura) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(lectura);
        return new CatedraRestClient(RestClient.builder().baseUrl("http://localhost:" + catedra.port())
                .requestFactory(factory).build());
    }

    private void responde(int estado, String cuerpo) {
        catedra.stubFor(get(urlPathEqualTo(RUTA)).willReturn(aResponse().withStatus(estado)
                .withHeader("Content-Type", estado >= 400 ? "application/problem+json" : "application/json")
                .withBody(cuerpo)));
    }

    @Test
    void pide_las_ocupaciones_con_el_profesional_y_el_rango() {
        responde(200, """
                {"professionalId":15,"from":"2026-09-14","to":"2026-09-20","occupancies":[]}""");

        client(Duration.ofSeconds(2)).findOccupancies(15, DESDE, HASTA);

        catedra.verify(1, getRequestedFor(urlPathEqualTo(RUTA))
                .withQueryParam("professionalId", equalTo("15"))
                .withQueryParam("from", equalTo("2026-09-14"))
                .withQueryParam("to", equalTo("2026-09-20")));
    }

    @Test
    void mapea_confirmadas_y_retenidas_e_ignora_campos_desconocidos() {
        responde(200, """
                {"professionalId":15,"from":"2026-09-14","to":"2026-09-20","campoNuevo":1,"occupancies":[
                  {"date":"2026-09-14","startTime":"09:00:00","status":"CONFIRMED","expiresAt":null},
                  {"date":"2026-09-14","startTime":"09:30:00","status":"HELD","expiresAt":"2026-09-10T14:44:50Z","otro":true}]}""");

        OccupanciesResponse respuesta = client(Duration.ofSeconds(2)).findOccupancies(15, DESDE, HASTA);

        assertThat(respuesta.occupancies()).containsExactly(
                new OccupanciesResponse.Item(DESDE, LocalTime.of(9, 0), "CONFIRMED"),
                new OccupanciesResponse.Item(DESDE, LocalTime.of(9, 30), "HELD"));
    }

    @Test
    void una_lista_vacia_es_una_respuesta_valida() {
        responde(200, """
                {"professionalId":15,"from":"2026-09-14","to":"2026-09-20","occupancies":[]}""");

        assertThat(client(Duration.ofSeconds(2)).findOccupancies(15, DESDE, HASTA).occupancies()).isEmpty();
    }

    @Test
    void una_respuesta_sin_la_lista_es_un_error_de_integracion() {
        responde(200, "{\"professionalId\":15}");

        assertThatThrownBy(() -> client(Duration.ofSeconds(2)).findOccupancies(15, DESDE, HASTA))
                .isInstanceOf(CatedraIntegrationException.class);
    }

    @Test
    void un_cuerpo_ilegible_es_un_error_de_integracion() {
        responde(200, "esto no es json");

        assertThatThrownBy(() -> client(Duration.ofSeconds(2)).findOccupancies(15, DESDE, HASTA))
                .isInstanceOf(CatedraIntegrationException.class);
    }

    @Test
    void los_400_401_y_403_son_errores_de_integracion_y_no_se_reintentan() {
        for (int estado : new int[]{400, 401, 403}) {
            catedra.resetAll();
            responde(estado, "{\"code\":\"INVALID_DATE_RANGE\"}");

            assertThatThrownBy(() -> client(Duration.ofSeconds(2)).findOccupancies(15, DESDE, HASTA))
                    .isInstanceOf(CatedraIntegrationException.class)
                    .hasMessageContaining(String.valueOf(estado));
            catedra.verify(1, getRequestedFor(urlPathEqualTo(RUTA)));
        }
    }

    @Test
    void los_5xx_son_cátedra_no_disponible() {
        for (int estado : new int[]{500, 503}) {
            catedra.resetAll();
            responde(estado, "{}");

            assertThatThrownBy(() -> client(Duration.ofSeconds(2)).findOccupancies(15, DESDE, HASTA))
                    .isInstanceOf(CatedraUnavailableException.class)
                    .hasMessageContaining(String.valueOf(estado));
        }
    }

    @Test
    void una_catedra_demasiado_lenta_es_no_disponible() {
        catedra.stubFor(get(urlPathEqualTo(RUTA)).willReturn(aResponse().withStatus(200).withFixedDelay(1500)
                .withHeader("Content-Type", "application/json").withBody("{\"occupancies\":[]}")));

        assertThatThrownBy(() -> client(Duration.ofMillis(300)).findOccupancies(15, DESDE, HASTA))
                .isInstanceOf(CatedraUnavailableException.class);
    }

    @Test
    void una_catedra_apagada_es_no_disponible() {
        CatedraRestClient client = client(Duration.ofSeconds(2));
        catedra.stop();

        assertThatThrownBy(() -> client.findOccupancies(15, DESDE, HASTA)).isInstanceOf(CatedraUnavailableException.class);
    }
}
