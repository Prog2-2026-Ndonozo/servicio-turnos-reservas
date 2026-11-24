package com.ndonozo.servicioturnosreservas.reservation.infrastructure.web;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T7 de punta a punta: un usuario real con su JWT pide reservar un turno; turnos lo valida contra un catálogo simulado
 * (JWT técnico RS256), le pide el hold a una cátedra simulada (JWT técnico de la cuenta) y guarda el proceso en un
 * PostgreSQL real. 2027-03-01 es lunes: las fechas son del futuro lejano para no depender del reloj.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "app.security.jwt.secret=" + ReservationApiIntegrationTest.SECRETO,
        "app.catalog.read-timeout=500ms",
        "app.catedra.read-timeout=500ms"})
class ReservationApiIntegrationTest {

    // 64 bytes en Base64, solo para pruebas
    static final String SECRETO =
            "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBw==";
    private static final String JWT_DE_LA_CATEDRA = "jwt-de-la-catedra-de-prueba";

    private static final String AGENDA = "/api/professionals/15/agenda";
    private static final String HOLDS = "/api/appointment-holds";
    private static final String RESERVAS = "/api/reservations";
    private static final String CUERPO = "{\"professionalId\":15,\"date\":\"2027-03-01\",\"startTime\":\"09:30\"}";

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

    @Autowired
    private JdbcTemplate jdbc;

    private String tokenDeUsuario;
    private long idDelUsuario;

    @BeforeEach
    void usuarioLogueadoYSistemasLimpios() throws Exception {
        CATALOGO.resetAll();
        CATEDRA.resetAll();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"login\":\"kmp\",\"password\":\"clave1234\",\"email\":\"kmp@correo.com\",\"langKey\":\"es\"}"));
        String cuerpo = mockMvc.perform(MockMvcRequestBuilders.post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"kmp\",\"password\":\"clave1234\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        tokenDeUsuario = JsonPath.read(cuerpo, "$.id_token");
        idDelUsuario = jdbc.queryForObject("SELECT id FROM app_user WHERE login = 'kmp'", Long.class);
    }

    @AfterAll
    static void apagaLosSistemas() {
        CATALOGO.stop();
        CATEDRA.stop();
    }

    // ---------- el camino feliz ----------

    @Test
    void crea_el_hold_en_la_catedra_con_el_id_del_usuario_y_guarda_el_proceso_asociado_a_el() throws Exception {
        agenda(true);
        hold(201, """
                {"holdId":"hold-feliz","reservationProcessId":"process-feliz","status":"HELD","expiresAt":"2027-03-01T08:00:00Z"}""");

        reservar(CUERPO)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationProcessId").value("process-feliz"))
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.expiresAt").value("2027-03-01T08:00:00Z"))
                .andExpect(content().string(not(containsString("hold-feliz"))));

        CATEDRA.verify(1, postRequestedFor(urlPathEqualTo(HOLDS))
                .withHeader("Authorization", equalTo("Bearer " + JWT_DE_LA_CATEDRA))
                .withRequestBody(equalToJson("""
                        {"professionalId":15,"date":"2027-03-01","startTime":"09:30","externalPatientId":"user-%d"}"""
                        .formatted(idDelUsuario))));
        Map<String, Object> fila = unaFila("process-feliz");
        assertThat(fila).containsEntry("user_id", idDelUsuario).containsEntry("hold_id", "hold-feliz")
                .containsEntry("professional_id", 15L).containsEntry("status", "HELD")
                .containsEntry("external_patient_id", "user-" + idDelUsuario);
        assertThat(fila.get("slot_date").toString()).isEqualTo("2027-03-01");
        assertThat(fila.get("slot_start_time").toString()).isEqualTo("09:30:00");
    }

    @Test
    void a_ningun_sistema_viaja_el_token_del_usuario() throws Exception {
        agenda(true);
        hold(201, """
                {"holdId":"hold-tokens","reservationProcessId":"process-tokens","status":"HELD","expiresAt":"2027-03-01T08:00:00Z"}""");

        reservar(CUERPO).andExpect(status().isCreated());

        String alCatalogo = CATALOGO.getAllServeEvents().getLast().getRequest().getHeader("Authorization");
        String ALaCatedra = CATEDRA.getAllServeEvents().getLast().getRequest().getHeader("Authorization");
        assertThat(alCatalogo).startsWith("Bearer ").isNotEqualTo("Bearer " + tokenDeUsuario);
        assertThat(ALaCatedra).isEqualTo("Bearer " + JWT_DE_LA_CATEDRA);
    }

    // ---------- el turno se valida antes de tocar la cátedra ----------

    @Test
    void un_turno_que_no_esta_en_la_agenda_responde_400_y_no_se_le_pide_nada_a_la_catedra() throws Exception {
        agenda(true);

        reservar("{\"professionalId\":15,\"date\":\"2027-03-01\",\"startTime\":\"09:15\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SLOT"));

        CATEDRA.verify(0, postRequestedFor(urlPathEqualTo(HOLDS)));
    }

    @Test
    void un_profesional_deshabilitado_responde_400_y_no_se_le_pide_nada_a_la_catedra() throws Exception {
        agenda(false);

        reservar(CUERPO).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROFESSIONAL_DISABLED"));

        CATEDRA.verify(0, postRequestedFor(urlPathEqualTo(HOLDS)));
    }

    @Test
    void un_profesional_inexistente_responde_404() throws Exception {
        CATALOGO.stubFor(get(urlPathEqualTo(AGENDA)).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/problem+json").withBody("{\"code\":\"PROFESSIONAL_NOT_FOUND\"}")));

        reservar(CUERPO).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROFESSIONAL_NOT_FOUND"));

        CATEDRA.verify(0, postRequestedFor(urlPathEqualTo(HOLDS)));
    }

    @Test
    void con_el_catalogo_caido_no_se_inicia_nada_y_responde_503() throws Exception {
        CATALOGO.stubFor(get(urlPathEqualTo(AGENDA)).willReturn(aResponse().withStatus(503)));

        reservar(CUERPO).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));

        CATEDRA.verify(0, postRequestedFor(urlPathEqualTo(HOLDS)));
    }

    // ---------- lo que responde la cátedra ----------

    @Test
    void un_turno_retenido_o_reservado_responde_409_y_no_se_guarda_nada() throws Exception {
        agenda(true);
        int antes = cantidadDeProcesos();

        rechazaCatedra(409, "SLOT_ALREADY_HELD");
        reservar(CUERPO).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLOT_ALREADY_HELD"));
        rechazaCatedra(409, "SLOT_ALREADY_RESERVED");
        reservar(CUERPO).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLOT_ALREADY_RESERVED"));

        assertThat(cantidadDeProcesos()).isEqualTo(antes);
    }

    @Test
    void si_la_catedra_rechaza_el_turno_que_el_catalogo_dio_por_valido_se_informa_su_codigo() throws Exception {
        agenda(true);

        rechazaCatedra(400, "INVALID_SLOT");
        reservar(CUERPO).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SLOT"));
        rechazaCatedra(400, "PROFESSIONAL_DISABLED");
        reservar(CUERPO).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROFESSIONAL_DISABLED"));
        rechazaCatedra(404, "PROFESSIONAL_NOT_FOUND");
        reservar(CUERPO).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROFESSIONAL_NOT_FOUND"));
    }

    @Test
    void una_catedra_caida_responde_503_el_POST_no_se_repite_y_no_se_guarda_nada() throws Exception {
        agenda(true);
        int antes = cantidadDeProcesos();
        hold(503, "{}");

        reservar(CUERPO).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATEDRA_UNAVAILABLE"));

        CATEDRA.verify(1, postRequestedFor(urlPathEqualTo(HOLDS)));
        assertThat(cantidadDeProcesos()).isEqualTo(antes);
    }

    @Test
    void un_jwt_tecnico_rechazado_por_la_catedra_responde_502_sin_reintentar_y_sin_filtrar_nada() throws Exception {
        agenda(true);
        hold(401, "{\"title\":\"Unauthorized\"}");

        reservar(CUERPO).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CATEDRA_ERROR"))
                .andExpect(content().string(not(containsString(JWT_DE_LA_CATEDRA))));

        CATEDRA.verify(1, postRequestedFor(urlPathEqualTo(HOLDS)));
    }

    // ---------- validación y protección ----------

    @Test
    void un_pedido_invalido_no_llega_a_ningun_sistema() throws Exception {
        reservar("{\"professionalId\":15,\"date\":\"2027-03-01\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(CATALOGO.getAllServeEvents()).isEmpty();
        assertThat(CATEDRA.getAllServeEvents()).isEmpty();
    }

    @Test
    void sin_token_responde_401_y_no_toca_ningun_sistema() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post(RESERVAS).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());

        assertThat(CATALOGO.getAllServeEvents()).isEmpty();
        assertThat(CATEDRA.getAllServeEvents()).isEmpty();
    }

    // ---------- ayudas ----------

    private ResultActions reservar(String cuerpo) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(RESERVAS).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private void agenda(boolean habilitado) {
        CATALOGO.stubFor(get(urlPathEqualTo(AGENDA)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"professional":{"id":15,"categoryId":101,"firstName":"Ana","lastName":"Torres","enabled":%s},
                         "weeklySchedules":[
                           {"id":1,"dayOfWeek":"MONDAY","startTime":"09:00:00","endTime":"11:00:00","slotDurationMinutes":30,"enabled":true}]}"""
                        .formatted(habilitado))));
    }

    private void hold(int estado, String cuerpo) {
        CATEDRA.stubFor(post(urlPathEqualTo(HOLDS)).willReturn(aResponse().withStatus(estado)
                .withHeader("Content-Type", estado >= 400 ? "application/problem+json" : "application/json")
                .withBody(cuerpo)));
    }

    private void rechazaCatedra(int estado, String code) {
        hold(estado, "{\"title\":\"x\",\"status\":" + estado + ",\"code\":\"" + code + "\"}");
    }

    private Map<String, Object> unaFila(String reservationProcessId) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT * FROM reservation_process WHERE reservation_process_id = ?", reservationProcessId);
        assertThat(filas).hasSize(1);
        return filas.getFirst();
    }

    private int cantidadDeProcesos() {
        return jdbc.queryForObject("SELECT count(*) FROM reservation_process", Integer.class);
    }
}
