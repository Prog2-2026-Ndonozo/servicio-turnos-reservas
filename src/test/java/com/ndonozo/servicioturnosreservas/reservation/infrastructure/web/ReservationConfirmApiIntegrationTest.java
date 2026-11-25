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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
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
 * T8a de punta a punta: un usuario real confirma su hold. Turnos lo busca en un PostgreSQL real, llama a una cátedra
 * simulada (con el JWT técnico de la cuenta) y deja el proceso en WAITING_FOR_PHONE. El proceso se siembra por SQL
 * porque crearlo ya lo cubre ReservationApiIntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "app.security.jwt.secret=" + ReservationConfirmApiIntegrationTest.SECRETO,
        "app.catedra.read-timeout=500ms"})
class ReservationConfirmApiIntegrationTest {

    // 64 bytes en Base64, solo para pruebas
    static final String SECRETO =
            "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBw==";
    private static final String JWT_DE_LA_CATEDRA = "jwt-de-la-catedra-de-prueba";
    private static final String CONFIRMAR_EN_CATEDRA = "/api/appointment-holds/hold-1/confirm";
    private static final String RUTA = "/api/reservations/process-1/confirm";
    private static final String CUERPO = "{\"firstName\":\"Juan\",\"lastName\":\"Perez\"}";

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
    void usuarioLogueadoYProcesoRetenido() throws Exception {
        CATEDRA.resetAll();
        jdbc.update("DELETE FROM reservation_process");
        mockMvc.perform(MockMvcRequestBuilders.post("/api/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"login\":\"kmp\",\"password\":\"clave1234\",\"email\":\"kmp@correo.com\",\"langKey\":\"es\"}"));
        String cuerpo = mockMvc.perform(MockMvcRequestBuilders.post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"kmp\",\"password\":\"clave1234\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        tokenDeUsuario = JsonPath.read(cuerpo, "$.id_token");
        idDelUsuario = jdbc.queryForObject("SELECT id FROM app_user WHERE login = 'kmp'", Long.class);
        proceso("process-1", "hold-1", "HELD", Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    @AfterAll
    static void apagaLosSistemas() {
        CATALOGO.stop();
        CATEDRA.stop();
    }

    @Test
    void confirma_en_la_catedra_con_los_datos_del_usuario_y_deja_el_proceso_en_WAITING_FOR_PHONE() throws Exception {
        catedra(202, """
                {"reservationProcessId":"process-1","status":"WAITING_FOR_PHONE","expiresAt":"2027-03-01T08:00:00Z"}""");

        confirmar(RUTA, CUERPO)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.reservationProcessId").value("process-1"))
                .andExpect(jsonPath("$.status").value("WAITING_FOR_PHONE"))
                .andExpect(content().string(not(containsString("hold-1"))));

        CATEDRA.verify(1, postRequestedFor(urlPathEqualTo(CONFIRMAR_EN_CATEDRA))
                .withHeader("Authorization", equalTo("Bearer " + JWT_DE_LA_CATEDRA))
                .withRequestBody(equalToJson("""
                        {"externalPatientId":"user-%d","patientFirstName":"Juan","patientLastName":"Perez"}"""
                        .formatted(idDelUsuario))));
        assertThat(estado("process-1")).isEqualTo("WAITING_FOR_PHONE");
    }

    @Test
    void confirmar_dos_veces_es_idempotente_y_la_segunda_no_llama_a_la_catedra() throws Exception {
        catedra(202, """
                {"reservationProcessId":"process-1","status":"WAITING_FOR_PHONE","expiresAt":"2027-03-01T08:00:00Z"}""");

        confirmar(RUTA, CUERPO).andExpect(status().isAccepted());
        confirmar(RUTA, CUERPO).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("WAITING_FOR_PHONE"));

        CATEDRA.verify(1, postRequestedFor(urlPathEqualTo(CONFIRMAR_EN_CATEDRA)));
    }

    @Test
    void si_la_catedra_ya_lo_tenia_confirmado_reconcilia_y_responde_202() throws Exception {
        rechaza(409, "RESERVATION_PROCESS_ALREADY_CONFIRMED");

        confirmar(RUTA, CUERPO).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("WAITING_FOR_PHONE"));

        assertThat(estado("process-1")).isEqualTo("WAITING_FOR_PHONE");
    }

    @Test
    void el_proceso_de_otro_usuario_responde_404_y_no_toca_la_catedra() throws Exception {
        jdbc.update("UPDATE reservation_process SET user_id = (SELECT id FROM app_user WHERE login <> 'kmp' LIMIT 1)"
                + " WHERE false");
        long otro = jdbc.queryForObject("""
                INSERT INTO app_user (login, password_hash, email, lang_key)
                VALUES ('otro', repeat('x', 60), 'otro@correo.com', 'es') ON CONFLICT (login) DO UPDATE SET login = 'otro'
                RETURNING id""", Long.class);
        jdbc.update("UPDATE reservation_process SET user_id = ?", otro);

        confirmar(RUTA, CUERPO).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));

        assertThat(CATEDRA.getAllServeEvents()).isEmpty();
        assertThat(estado("process-1")).isEqualTo("HELD");
    }

    @Test
    void un_proceso_inexistente_responde_404() throws Exception {
        confirmar("/api/reservations/no-existe/confirm", CUERPO).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void un_hold_vencido_localmente_responde_409_lo_cierra_como_EXPIRED_y_no_llama_a_la_catedra() throws Exception {
        jdbc.update("UPDATE reservation_process SET expires_at = now() - interval '1 minute'");

        confirmar(RUTA, CUERPO).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));

        assertThat(estado("process-1")).isEqualTo("EXPIRED");
        assertThat(CATEDRA.getAllServeEvents()).isEmpty();
    }

    @Test
    void si_la_catedra_dice_que_el_hold_vencio_responde_409_y_lo_cierra_como_EXPIRED() throws Exception {
        rechaza(404, "HOLD_EXPIRED");

        confirmar(RUTA, CUERPO).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));

        assertThat(estado("process-1")).isEqualTo("EXPIRED");
    }

    @Test
    void un_proceso_ya_vencido_no_se_puede_confirmar() throws Exception {
        jdbc.update("UPDATE reservation_process SET status = 'EXPIRED'");

        confirmar(RUTA, CUERPO).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_RESERVATION_STATE"));

        assertThat(CATEDRA.getAllServeEvents()).isEmpty();
    }

    @Test
    void si_otro_reservo_el_turno_responde_409_y_el_proceso_no_cambia() throws Exception {
        rechaza(409, "SLOT_ALREADY_RESERVED");

        confirmar(RUTA, CUERPO).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLOT_ALREADY_RESERVED"));

        assertThat(estado("process-1")).isEqualTo("HELD");
    }

    @Test
    void una_catedra_caida_responde_503_y_el_proceso_no_cambia_ni_se_reintenta() throws Exception {
        catedra(503, "{}");

        confirmar(RUTA, CUERPO).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATEDRA_UNAVAILABLE"));

        CATEDRA.verify(1, postRequestedFor(urlPathEqualTo(CONFIRMAR_EN_CATEDRA)));
        assertThat(estado("process-1")).isEqualTo("HELD");
    }

    @Test
    void un_rechazo_de_integracion_de_la_catedra_responde_502_sin_mostrar_el_jwt() throws Exception {
        rechaza(403, "HOLD_OWNER_MISMATCH");

        confirmar(RUTA, CUERPO).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("CATEDRA_ERROR"))
                .andExpect(content().string(not(containsString(JWT_DE_LA_CATEDRA))));
    }

    @Test
    void un_cuerpo_invalido_o_sin_token_no_llega_a_la_catedra() throws Exception {
        confirmar(RUTA, "{\"firstName\":\"J\",\"lastName\":\"Perez\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(MockMvcRequestBuilders.post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());

        assertThat(CATEDRA.getAllServeEvents()).isEmpty();
    }

    // ---------- ayudas ----------

    private ResultActions confirmar(String ruta, String cuerpo) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private void catedra(int estado, String cuerpo) {
        CATEDRA.stubFor(post(urlPathEqualTo(CONFIRMAR_EN_CATEDRA)).willReturn(aResponse().withStatus(estado)
                .withHeader("Content-Type", estado >= 400 ? "application/problem+json" : "application/json")
                .withBody(cuerpo)));
    }

    private void rechaza(int estado, String code) {
        catedra(estado, "{\"title\":\"x\",\"status\":" + estado + ",\"code\":\"" + code + "\"}");
    }

    private void proceso(String processId, String holdId, String estado, Instant vence) {
        jdbc.update("""
                INSERT INTO reservation_process (user_id, reservation_process_id, hold_id, professional_id, slot_date,
                    slot_start_time, external_patient_id, status, expires_at)
                VALUES (?, ?, ?, 15, DATE '2027-03-01', TIME '09:30', ?, ?, ?)""",
                idDelUsuario, processId, holdId, "user-" + idDelUsuario, estado, java.sql.Timestamp.from(vence));
    }

    private String estado(String reservationProcessId) {
        return jdbc.queryForObject("SELECT status FROM reservation_process WHERE reservation_process_id = ?",
                String.class, reservationProcessId);
    }
}
