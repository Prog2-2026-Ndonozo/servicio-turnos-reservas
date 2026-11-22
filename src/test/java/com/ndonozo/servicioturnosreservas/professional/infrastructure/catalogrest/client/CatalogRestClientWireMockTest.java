package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.ndonozo.servicioturnosreservas.professional.application.exception.CatalogIntegrationException;
import com.ndonozo.servicioturnosreservas.professional.application.exception.CatalogUnavailableException;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalSearchCriteria;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.config.CatalogRestConfig;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.ProfessionalAgendaResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.ProfessionalCategoryResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.ProfessionalResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.token.CatalogTokenIssuer;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.token.CatalogTokenProvider;
import com.ndonozo.servicioturnosreservas.support.TestRsaKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * El cliente contra un servidor HTTP real (WireMock): pedidos, token, reintento del 401, traducción de errores y tiempos
 * de espera. Los tokens se firman con un par de claves RS256 generado en el test y se verifican con la pública.
 */
@ExtendWith(OutputCaptureExtension.class)
class CatalogRestClientWireMockTest {

    private static final String BUSQUEDA = "/api/professionals/search";
    private static final String CATEGORIAS = "/api/professional-categories";
    private static final String AGENDA = "/api/professionals/201/agenda";

    private WireMockServer catalogo;
    private CatalogRestClient client;

    @BeforeEach
    void arrancaElCatalogoSimulado() {
        catalogo = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        catalogo.start();
        client = clienteConLectura(Duration.ofSeconds(2));
    }

    @AfterEach
    void apagaElCatalogoSimulado() {
        catalogo.stop();
    }

    // ---------- búsqueda ----------

    @Test
    void la_busqueda_sin_filtros_no_manda_parametros_y_mapea_la_lista() {
        responde(BUSQUEDA, 200, """
                [{"id":15,"categoryId":101,"firstName":"Ana","lastName":"Gomez","enabled":true}]""");

        List<ProfessionalResponse> resultado = client.searchProfessionals(ProfessionalSearchCriteria.withoutFilters());

        assertThat(resultado).containsExactly(new ProfessionalResponse(15L, 101L, "Ana", "Gomez", true));
        catalogo.verify(getRequestedFor(urlPathEqualTo(BUSQUEDA)));
        assertThat(catalogo.getAllServeEvents().getFirst().getRequest().getQueryParams()).isEmpty();
    }

    @Test
    void la_busqueda_manda_cada_filtro_con_su_nombre_y_formato() {
        responde(BUSQUEDA, 200, "[]");

        client.searchProfessionals(new ProfessionalSearchCriteria(101L, "ana", true, LocalDate.of(2026, 11, 23)));

        catalogo.verify(getRequestedFor(urlPathEqualTo(BUSQUEDA))
                .withQueryParam("categoryId", equalTo("101"))
                .withQueryParam("name", equalTo("ana"))
                .withQueryParam("enabled", equalTo("true"))
                .withQueryParam("availableOn", equalTo("2026-11-23")));
    }

    @Test
    void la_busqueda_solo_manda_los_filtros_que_se_pidieron() {
        responde(BUSQUEDA, 200, "[]");

        client.searchProfessionals(new ProfessionalSearchCriteria(null, "ana", null, null));

        var pedido = catalogo.getAllServeEvents().getFirst().getRequest();
        assertThat(pedido.getQueryParams().keySet()).containsExactly("name");
    }

    @Test
    void el_nombre_se_codifica_por_completo_aunque_traiga_caracteres_especiales() {
        responde(BUSQUEDA, 200, "[]");
        String nombre = "ana & torres+x=1/ñ #{valor} ?";

        client.searchProfessionals(new ProfessionalSearchCriteria(null, nombre, null, null));

        catalogo.verify(getRequestedFor(urlPathEqualTo(BUSQUEDA)).withQueryParam("name", equalTo(nombre)));
    }

    @Test
    void una_busqueda_sin_coincidencias_devuelve_una_lista_vacia() {
        responde(BUSQUEDA, 200, "[]");

        assertThat(client.searchProfessionals(ProfessionalSearchCriteria.withoutFilters())).isEmpty();
    }

    // ---------- categorías y agenda ----------

    @Test
    void las_categorias_se_mapean() {
        responde(CATEGORIAS, 200, """
                [{"id":101,"name":"Cardiologia","description":"Atencion cardiovascular","enabled":true}]""");

        assertThat(client.listCategories()).containsExactly(
                new ProfessionalCategoryResponse(101L, "Cardiologia", "Atencion cardiovascular", true));
    }

    @Test
    void la_agenda_se_mapea_con_dias_horas_y_horarios_deshabilitados() {
        responde(AGENDA, 200, """
                {"professional":{"id":201,"categoryId":101,"firstName":"Ana","lastName":"Torres","enabled":false},
                 "weeklySchedules":[
                   {"id":301,"dayOfWeek":"MONDAY","startTime":"12:00:00","endTime":"15:00:00","slotDurationMinutes":30,"enabled":true},
                   {"id":302,"dayOfWeek":"WEDNESDAY","startTime":"17:00:00","endTime":"20:00:00","slotDurationMinutes":30,"enabled":false}]}""");

        ProfessionalAgendaResponse agenda = client.findAgenda(201).orElseThrow();

        assertThat(agenda.professional().enabled()).isFalse();
        assertThat(agenda.weeklySchedules()).hasSize(2);
        assertThat(agenda.weeklySchedules().get(0).dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(agenda.weeklySchedules().get(0).startTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(agenda.weeklySchedules().get(0).slotDurationMinutes()).isEqualTo(30);
        assertThat(agenda.weeklySchedules().get(1).enabled()).isFalse();
    }

    @Test
    void una_agenda_sin_horarios_es_valida() {
        responde(AGENDA, 200, """
                {"professional":{"id":201,"categoryId":101,"firstName":"Ana","lastName":"Torres","enabled":true},
                 "weeklySchedules":[]}""");

        assertThat(client.findAgenda(201).orElseThrow().weeklySchedules()).isEmpty();
    }

    @Test
    void un_profesional_inexistente_es_un_Optional_vacio() {
        responde(AGENDA, 404, """
                {"type":"about:blank","title":"Not Found","status":404,
                 "detail":"No existe un profesional con id 201","code":"PROFESSIONAL_NOT_FOUND"}""");

        assertThat(client.findAgenda(201)).isEqualTo(Optional.empty());
    }

    @Test
    void los_campos_que_el_cliente_no_conoce_se_ignoran() {
        responde(CATEGORIAS, 200, """
                [{"id":101,"name":"Cardiologia","description":"","enabled":true,"campoNuevo":"x","otro":{"a":1}}]""");

        assertThat(client.listCategories()).hasSize(1);
    }

    // ---------- el token ----------

    @Test
    void cada_pedido_lleva_un_bearer_RS256_valido_con_los_claims_del_contrato() {
        responde(CATEGORIAS, 200, "[]");

        client.listCategories();

        Jwt jwt = decodificar(tokenDelPedido(0));
        assertThat(jwt.getClaims().get("iss").toString()).isEqualTo("servicio-turnos");
        assertThat(jwt.getAudience()).containsExactly("servicio-catalogo");
        assertThat(jwt.<String>getClaim("scope")).isEqualTo("catalog:read");
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("RS256");
        assertThat(jwt.hasClaim("userId")).isFalse();
    }

    @Test
    void el_mismo_token_se_reutiliza_entre_pedidos() {
        responde(CATEGORIAS, 200, "[]");

        client.listCategories();
        client.listCategories();

        assertThat(tokenDelPedido(0)).isEqualTo(tokenDelPedido(1));
    }

    @Test
    void ante_un_401_emite_un_token_nuevo_y_reintenta_una_sola_vez(CapturedOutput salida) {
        catalogo.stubFor(get(urlPathEqualTo(CATEGORIAS)).inScenario("401").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(401)).willSetStateTo("token-nuevo"));
        catalogo.stubFor(get(urlPathEqualTo(CATEGORIAS)).inScenario("401").whenScenarioStateIs("token-nuevo")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("[]")));

        List<ProfessionalCategoryResponse> resultado = client.listCategories();

        assertThat(resultado).isEmpty();
        assertThat(catalogo.getAllServeEvents()).hasSize(2);
        assertThat(tokenDelPedido(0)).isNotEqualTo(tokenDelPedido(1));
        assertThat(salida.getAll()).contains("se reintenta una sola vez");
    }

    @Test
    void dos_401_seguidos_son_un_error_de_integracion_tras_exactamente_dos_pedidos() {
        catalogo.stubFor(get(urlPathEqualTo(CATEGORIAS)).willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> client.listCategories())
                .isInstanceOf(CatalogIntegrationException.class)
                .hasMessageContaining("401");
        assertThat(catalogo.getAllServeEvents()).hasSize(2);
    }

    @Test
    void ni_los_mensajes_ni_los_logs_incluyen_el_token(CapturedOutput salida) {
        catalogo.stubFor(get(urlPathEqualTo(CATEGORIAS)).willReturn(aResponse().withStatus(401)));

        Throwable error = catchThrowable(() -> client.listCategories());

        String token = tokenDelPedido(0);
        assertThat(error.getMessage()).doesNotContain(token);
        assertThat(salida.getAll()).doesNotContain(token);
    }

    // ---------- errores ----------

    @Test
    void un_403_es_un_error_de_integracion_y_no_se_reintenta() {
        responde(CATEGORIAS, 403, "{\"code\":\"FORBIDDEN\"}");

        assertThatThrownBy(() -> client.listCategories())
                .isInstanceOf(CatalogIntegrationException.class)
                .hasMessageContaining("403");
        assertThat(catalogo.getAllServeEvents()).hasSize(1);
    }

    @Test
    void un_400_es_un_error_de_integracion() {
        responde(BUSQUEDA, 400, "{\"code\":\"VALIDATION_ERROR\"}");

        assertThatThrownBy(() -> client.searchProfessionals(ProfessionalSearchCriteria.withoutFilters()))
                .isInstanceOf(CatalogIntegrationException.class)
                .hasMessageContaining("400");
    }

    @Test
    void un_404_sin_code_en_la_agenda_es_un_error_de_integracion_no_un_profesional_inexistente() {
        responde(AGENDA, 404, "{\"timestamp\":\"2026-11-20\",\"status\":404,\"error\":\"Not Found\",\"path\":\"/x\"}");

        assertThatThrownBy(() -> client.findAgenda(201)).isInstanceOf(CatalogIntegrationException.class);
    }

    @Test
    void un_PROFESSIONAL_NOT_FOUND_fuera_de_la_agenda_es_un_error_de_integracion() {
        responde(BUSQUEDA, 404, "{\"code\":\"PROFESSIONAL_NOT_FOUND\"}");

        assertThatThrownBy(() -> client.searchProfessionals(ProfessionalSearchCriteria.withoutFilters()))
                .isInstanceOf(CatalogIntegrationException.class);
    }

    @Test
    void un_500_y_un_503_son_catalogo_no_disponible_y_no_se_reintentan() {
        responde(CATEGORIAS, 500, "");
        assertThatThrownBy(() -> client.listCategories()).isInstanceOf(CatalogUnavailableException.class)
                .hasMessageContaining("500");
        assertThat(catalogo.getAllServeEvents()).hasSize(1);

        catalogo.resetAll();
        responde(CATEGORIAS, 503, "");
        assertThatThrownBy(() -> client.listCategories()).isInstanceOf(CatalogUnavailableException.class)
                .hasMessageContaining("503");
        assertThat(catalogo.getAllServeEvents()).hasSize(1);
    }

    @Test
    void sin_conexion_es_catalogo_no_disponible() {
        catalogo.stop();

        assertThatThrownBy(() -> client.listCategories()).isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void si_el_catalogo_tarda_mas_que_el_tiempo_de_espera_es_catalogo_no_disponible() {
        catalogo.stubFor(get(urlPathEqualTo(CATEGORIAS)).willReturn(aResponse().withStatus(200).withFixedDelay(1500)
                .withHeader("Content-Type", "application/json").withBody("[]")));
        CatalogRestClient impaciente = clienteConLectura(Duration.ofMillis(300));

        long inicio = System.nanoTime();
        assertThatThrownBy(impaciente::listCategories).isInstanceOf(CatalogUnavailableException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofMillis(1200));
        assertThat(catalogo.getAllServeEvents()).hasSize(1);
    }

    @Test
    void una_respuesta_que_no_es_json_es_un_error_de_integracion() {
        responde(CATEGORIAS, 200, "esto no es json");

        assertThatThrownBy(() -> client.listCategories()).isInstanceOf(CatalogIntegrationException.class);
    }

    @Test
    void una_respuesta_vacia_es_un_error_de_integracion() {
        catalogo.stubFor(get(urlPathEqualTo(CATEGORIAS)).willReturn(aResponse().withStatus(200)));

        assertThatThrownBy(() -> client.listCategories()).isInstanceOf(CatalogIntegrationException.class);
    }

    // ---------- soporte ----------

    private void responde(String ruta, int estado, String cuerpo) {
        catalogo.stubFor(get(urlPathEqualTo(ruta)).willReturn(aResponse().withStatus(estado)
                .withHeader("Content-Type", estado >= 400 ? "application/problem+json" : "application/json")
                .withBody(cuerpo)));
    }

    private CatalogRestClient clienteConLectura(Duration lectura) {
        CatalogTokenIssuer issuer = new CatalogTokenIssuer(
                TestRsaKeys.privateKey(), "servicio-turnos", "servicio-catalogo", Clock.systemUTC());
        RestClient restClient = CatalogRestConfig.restClient(
                "http://localhost:" + catalogo.port(), Duration.ofSeconds(1), lectura);
        return new CatalogRestClient(restClient, new CatalogTokenProvider(issuer, Clock.systemUTC()));
    }

    /** El token que llegó en el pedido número n (en orden de llegada), sin la palabra Bearer. */
    private String tokenDelPedido(int n) {
        var eventos = catalogo.getAllServeEvents();
        var pedido = eventos.get(eventos.size() - 1 - n).getRequest();
        return pedido.getHeader("Authorization").substring("Bearer ".length());
    }

    private static Jwt decodificar(String token) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestRsaKeys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        return decoder.decode(token);
    }
}
