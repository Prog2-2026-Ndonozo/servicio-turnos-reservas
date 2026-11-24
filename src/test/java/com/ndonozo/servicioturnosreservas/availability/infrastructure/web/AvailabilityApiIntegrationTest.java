package com.ndonozo.servicioturnosreservas.availability.infrastructure.web;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.jayway.jsonpath.JsonPath;
import com.ndonozo.servicioturnosreservas.support.TestRsaKeys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T6 de punta a punta: un usuario real con su JWT pide la disponibilidad a turnos, que consulta la agenda a un catálogo
 * simulado (JWT técnico RS256) y las ocupaciones a una cátedra simulada (JWT técnico de la cuenta). Las fechas de los casos
 * felices son del futuro lejano para no depender del reloj: 2027-03-01 es lunes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "app.security.jwt.secret=" + AvailabilityApiIntegrationTest.SECRETO,
        "app.catalog.read-timeout=500ms",
        "app.catedra.read-timeout=500ms"})
class AvailabilityApiIntegrationTest {

    // 64 bytes en Base64, solo para pruebas
    static final String SECRETO =
            "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBw==";
    private static final String JWT_DE_LA_CATEDRA = "jwt-de-la-catedra-de-prueba";

    private static final String AGENDA = "/api/professionals/15/agenda";
    private static final String OCUPACIONES = "/api/appointment-occupancies";
    private static final String DISPONIBILIDAD = "/api/availability";

    static final WireMockServer CATALOGO = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    static final WireMockServer CATEDRA = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        CATALOGO.start();
        CATEDRA.start();
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configuracion(DynamicPropertyRegistry registry) {
        registry.add("app.catalog.jwt.private-key-file", () -> TestRsaKeys.privateKeyPemFile().toString());
        registry.add("app.catalog.base-url", () -> "http://localhost:" + CATALOGO.port());
        registry.add("app.catedra.base-url", () -> "http://localhost:" + CATEDRA.port());
        registry.add("app.catedra.jwt", () -> JWT_DE_LA_CATEDRA);
    }

    @Autowired
    private MockMvc mockMvc;

    private String tokenDeUsuario;

    @BeforeEach
    void usuarioLogueadoYSistemasLimpios() throws Exception {
        CATALOGO.resetAll();
        CATEDRA.resetAll();
        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"login\":\"kmp\",\"password\":\"clave1234\",\"email\":\"kmp@correo.com\",\"langKey\":\"es\"}"));
        String cuerpo = mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"kmp\",\"password\":\"clave1234\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        tokenDeUsuario = JsonPath.read(cuerpo, "$.id_token");
    }

    @AfterAll
    static void apagaLosSistemas() {
        CATALOGO.stop();
        CATEDRA.stop();
    }

    // ---------- el cálculo con los dos sistemas ----------

    @Test
    void los_turnos_libres_son_la_agenda_del_catalogo_menos_las_ocupaciones_de_la_catedra() throws Exception {
        agenda(true, """
                {"id":1,"dayOfWeek":"MONDAY","startTime":"09:00:00","endTime":"11:00:00","slotDurationMinutes":30,"enabled":true},
                {"id":2,"dayOfWeek":"MONDAY","startTime":"15:00:00","endTime":"16:00:00","slotDurationMinutes":30,"enabled":false}""");
        catedra(200, """
                {"professionalId":15,"from":"2027-03-01","to":"2027-03-01","occupancies":[
                  {"date":"2027-03-01","startTime":"09:00:00","status":"CONFIRMED","expiresAt":null},
                  {"date":"2027-03-01","startTime":"10:00:00","status":"HELD","expiresAt":"2027-03-01T08:00:00Z"}]}""");

        pedir("15", "2027-03-01", "2027-03-01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalId").value(15))
                .andExpect(jsonPath("$.slots.length()").value(2))
                .andExpect(jsonPath("$.slots[0].startTime").value("09:30:00"))
                .andExpect(jsonPath("$.slots[0].endTime").value("10:00:00"))
                .andExpect(jsonPath("$.slots[1].startTime").value("10:30:00"));

        CATEDRA.verify(1, getRequestedFor(urlPathEqualTo(OCUPACIONES))
                .withQueryParam("professionalId", equalTo("15"))
                .withQueryParam("from", equalTo("2027-03-01"))
                .withQueryParam("to", equalTo("2027-03-01")));
    }

    @Test
    void a_la_catedra_viaja_su_jwt_tecnico_y_ningun_sistema_recibe_el_token_del_usuario() throws Exception {
        agenda(true, "");
        catedra(200, "{\"occupancies\":[]}");

        pedir("15", "2027-03-01", "2027-03-07").andExpect(status().isOk());

        CATEDRA.verify(getRequestedFor(urlPathEqualTo(OCUPACIONES))
                .withHeader("Authorization", equalTo("Bearer " + JWT_DE_LA_CATEDRA)));
        String alCatalogo = CATALOGO.getAllServeEvents().getLast().getRequest().getHeader("Authorization");
        assertThat(alCatalogo).startsWith("Bearer ").isNotEqualTo("Bearer " + tokenDeUsuario)
                .isNotEqualTo("Bearer " + JWT_DE_LA_CATEDRA);
    }

    @Test
    void cada_consulta_vuelve_a_preguntarle_a_los_dos_sistemas() throws Exception {
        agenda(true, "");
        catedra(200, "{\"occupancies\":[]}");

        for (int i = 0; i < 3; i++) {
            pedir("15", "2027-03-01", "2027-03-07").andExpect(status().isOk());
        }

        CATALOGO.verify(3, getRequestedFor(urlPathEqualTo(AGENDA)));
        CATEDRA.verify(3, getRequestedFor(urlPathEqualTo(OCUPACIONES)));
    }

    @Test
    void los_dias_que_ya_pasaron_no_ofrecen_turnos() throws Exception {
        agenda(true, """
                {"id":1,"dayOfWeek":"MONDAY","startTime":"09:00:00","endTime":"10:00:00","slotDurationMinutes":30,"enabled":true}""");
        catedra(200, "{\"occupancies\":[]}");

        pedir("15", "2020-03-02", "2020-03-02").andExpect(status().isOk()).andExpect(jsonPath("$.slots.length()").value(0));
    }

    @Test
    void un_profesional_deshabilitado_devuelve_lista_vacia_sin_llamar_a_la_catedra() throws Exception {
        agenda(false, """
                {"id":1,"dayOfWeek":"MONDAY","startTime":"09:00:00","endTime":"10:00:00","slotDurationMinutes":30,"enabled":true}""");

        pedir("15", "2027-03-01", "2027-03-01").andExpect(status().isOk()).andExpect(jsonPath("$.slots.length()").value(0));

        CATEDRA.verify(0, getRequestedFor(urlPathEqualTo(OCUPACIONES)));
    }

    // ---------- errores ----------

    @Test
    void un_profesional_inexistente_responde_404_sin_llamar_a_la_catedra() throws Exception {
        CATALOGO.stubFor(get(urlPathEqualTo(AGENDA)).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"status\":404,\"code\":\"PROFESSIONAL_NOT_FOUND\"}")));

        pedir("15", "2027-03-01", "2027-03-01").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFESSIONAL_NOT_FOUND"));

        CATEDRA.verify(0, getRequestedFor(urlPathEqualTo(OCUPACIONES)));
    }

    @Test
    void el_catalogo_caido_responde_503_CATALOG_UNAVAILABLE() throws Exception {
        CATALOGO.stubFor(get(urlPathEqualTo(AGENDA)).willReturn(aResponse().withStatus(503)));

        pedir("15", "2027-03-01", "2027-03-01").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
    }

    @Test
    void el_catalogo_que_rechaza_el_token_tecnico_responde_502_CATALOG_ERROR() throws Exception {
        CATALOGO.stubFor(get(urlPathEqualTo(AGENDA)).willReturn(aResponse().withStatus(403)));

        pedir("15", "2027-03-01", "2027-03-01").andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CATALOG_ERROR"));
    }

    @Test
    void la_catedra_con_error_interno_responde_503_CATEDRA_UNAVAILABLE() throws Exception {
        agenda(true, "");
        catedra(503, "{}");

        pedir("15", "2027-03-01", "2027-03-01").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATEDRA_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("localhost"))));
    }

    @Test
    void la_catedra_demasiado_lenta_responde_503_CATEDRA_UNAVAILABLE() throws Exception {
        agenda(true, "");
        CATEDRA.stubFor(get(urlPathEqualTo(OCUPACIONES)).willReturn(aResponse().withStatus(200).withFixedDelay(1500)
                .withHeader("Content-Type", "application/json").withBody("{\"occupancies\":[]}")));

        pedir("15", "2027-03-01", "2027-03-01").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATEDRA_UNAVAILABLE"));
    }

    @Test
    void la_catedra_que_rechaza_el_jwt_responde_502_CATEDRA_ERROR_sin_mostrar_el_jwt() throws Exception {
        agenda(true, "");
        catedra(401, "{}");

        pedir("15", "2027-03-01", "2027-03-01").andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CATEDRA_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(JWT_DE_LA_CATEDRA))));
        CATEDRA.verify(1, getRequestedFor(urlPathEqualTo(OCUPACIONES)));
    }

    @Test
    void un_rango_invalido_responde_400_sin_llamar_a_nadie() throws Exception {
        pedir("15", "2027-03-07", "2027-03-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        pedir("15", "2027-03-01", "2027-04-15").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        pedir("abc", "2027-03-01", "2027-03-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(CATALOGO.getAllServeEvents()).isEmpty();
        assertThat(CATEDRA.getAllServeEvents()).isEmpty();
    }

    @Test
    void sin_token_de_usuario_responde_401_y_no_se_llama_a_nadie() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(DISPONIBILIDAD).queryParam("professionalId", "15").queryParam("from", "2027-03-01")
                        .queryParam("to", "2027-03-01"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(CATALOGO.getAllServeEvents()).isEmpty();
        assertThat(CATEDRA.getAllServeEvents()).isEmpty();
    }

    // ---------- ayudas ----------

    private ResultActions pedir(String profesional, String desde, String hasta) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(DISPONIBILIDAD).queryParam("professionalId", profesional)
                .queryParam("from", desde).queryParam("to", hasta)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario));
    }

    private void agenda(boolean habilitado, String horarios) {
        CATALOGO.stubFor(get(urlPathEqualTo(AGENDA)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"professional":{"id":15,"categoryId":101,"firstName":"Ana","lastName":"Torres","enabled":%s},
                         "weeklySchedules":[%s]}""".formatted(habilitado, horarios))));
    }

    private void catedra(int estado, String cuerpo) {
        CATEDRA.stubFor(get(urlPathEqualTo(OCUPACIONES)).willReturn(aResponse().withStatus(estado)
                .withHeader("Content-Type", estado >= 400 ? "application/problem+json" : "application/json")
                .withBody(cuerpo)));
    }
}
