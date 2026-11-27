package com.ndonozo.servicioturnosreservas.reservation.infrastructure.kafka;

import com.ndonozo.servicioturnosreservas.support.TestRsaKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * El listener de punta a punta: un broker de Kafka embebido le entrega mensajes reales y turnos los aplica sobre un
 * PostgreSQL real. Como el topic tiene una sola partición, los mensajes se procesan en el orden en que se publican: un
 * mensaje marcador al final prueba que los anteriores ya se consumieron.
 */
@SpringBootTest
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = AppointmentActionsKafkaIntegrationTest.TOPIC)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.group-id=grupo-del-test",
        "app.catedra.kafka.appointment-actions-topic=" + AppointmentActionsKafkaIntegrationTest.TOPIC,
        "app.security.jwt.secret=" + AppointmentActionsKafkaIntegrationTest.SECRETO})
class AppointmentActionsKafkaIntegrationTest {

    static final String TOPIC = "alumnos.turnos.acciones.prueba-kafka";
    // 64 bytes en Base64, solo para pruebas
    static final String SECRETO =
            "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBw==";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configuracion(DynamicPropertyRegistry registry) {
        registry.add("app.catalog.jwt.private-key-file", () -> TestRsaKeys.privateKeyPemFile().toString());
        registry.add("app.catedra.base-url", () -> "http://localhost:1");
        registry.add("app.catedra.jwt", () -> "jwt-de-prueba");
    }

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private JdbcTemplate jdbc;

    private long userId;

    @BeforeEach
    void hayUnUsuario() {
        jdbc.update("DELETE FROM reservation_process");
        jdbc.update("DELETE FROM processed_event");
        jdbc.update("DELETE FROM app_user");
        userId = jdbc.queryForObject("""
                INSERT INTO app_user (login, password_hash, email, lang_key)
                VALUES ('ana', repeat('x', 60), 'ana@correo.com', 'es') RETURNING id""", Long.class);
    }

    @Test
    void un_pedido_de_telefono_pasa_el_proceso_a_WAITING_FOR_PHONE_y_guarda_su_eventId() {
        proceso("process-1", "hold-1", "HELD");

        publica(pedido("evt-1", "process-1", "hold-1"));
        esperaAlMarcador();

        assertThat(estado("process-1")).isEqualTo("WAITING_FOR_PHONE");
        assertThat(eventId("process-1")).isEqualTo("evt-1");
    }

    @Test
    void el_mismo_evento_dos_veces_se_aplica_una_sola_vez() {
        proceso("process-1", "hold-1", "HELD");

        publica(pedido("evt-1", "process-1", "hold-1"));
        publica(pedido("evt-1", "process-1", "hold-1"));
        esperaAlMarcador();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id = 'evt-1'", Integer.class)).isEqualTo(1);
        // una sola escritura: la versión pasó de 0 a 1 y no a 2
        assertThat(jdbc.queryForObject("SELECT version FROM reservation_process WHERE reservation_process_id = 'process-1'", Long.class)).isEqualTo(1L);
    }

    @Test
    void un_pedido_nuevo_reemplaza_al_anterior() {
        proceso("process-1", "hold-1", "HELD");

        publica(pedido("evt-1", "process-1", "hold-1"));
        publica(pedido("evt-2", "process-1", "hold-1"));
        esperaAlMarcador();

        assertThat(estado("process-1")).isEqualTo("WAITING_FOR_PHONE");
        assertThat(eventId("process-1")).isEqualTo("evt-2");
    }

    @Test
    void los_procesos_finales_no_se_alteran_y_el_evento_queda_registrado() {
        proceso("process-1", "hold-1", "EXPIRED");

        publica(pedido("evt-1", "process-1", "hold-1"));
        esperaAlMarcador();

        assertThat(estado("process-1")).isEqualTo("EXPIRED");
        assertThat(eventId("process-1")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id = 'evt-1'", Integer.class)).isEqualTo(1);
    }

    @Test
    void lo_ajeno_o_roto_se_descarta_y_el_listener_sigue_vivo() {
        proceso("process-1", "hold-1", "HELD");

        publica("esto no es json");
        publica(pedido("evt-desconocido", "no-existe", "hold-x"));
        publica(pedido("evt-otro-hold", "process-1", "otro-hold"));
        publica(pedido("evt-otro-tipo", "process-1", "hold-1").replace("AdditionalInformationRequested", "AppointmentConfirmed"));
        esperaAlMarcador();

        assertThat(estado("process-1")).isEqualTo("HELD");
        assertThat(eventId("process-1")).isNull();

        publica(pedido("evt-bueno", "process-1", "hold-1"));
        esperaAlMarcador();

        assertThat(estado("process-1")).isEqualTo("WAITING_FOR_PHONE");
        assertThat(eventId("process-1")).isEqualTo("evt-bueno");
    }

    @Test
    void la_confirmacion_pasa_el_proceso_a_CONFIRMED_y_guarda_el_reservationId() {
        proceso("process-1", "hold-1", "PHONE_SUBMITTED");

        publica(resultado("AppointmentConfirmed", "evt-1", "process-1", "hold-1", "\"reservationId\":8251,\"status\":\"CONFIRMED\""));
        esperaAlMarcador();

        assertThat(estado("process-1")).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT reservation_id FROM reservation_process WHERE reservation_process_id = 'process-1'", Long.class))
                .isEqualTo(8251L);
    }

    @Test
    void el_rechazo_vuelve_a_WAITING_FOR_PHONE_con_el_motivo_y_el_vencimiento_nuevo() {
        proceso("process-1", "hold-1", "PHONE_SUBMITTED");

        publica(resultado("AdditionalInformationRejected", "evt-1", "process-1", "hold-1",
                "\"reason\":\"INVALID_PHONE_NUMBER\",\"expiresAt\":\"2027-03-01T10:00:00Z\""));
        esperaAlMarcador();

        assertThat(estado("process-1")).isEqualTo("WAITING_FOR_PHONE");
        assertThat(jdbc.queryForObject("SELECT rejection_reason FROM reservation_process WHERE reservation_process_id = 'process-1'", String.class))
                .isEqualTo("INVALID_PHONE_NUMBER");
        assertThat(jdbc.queryForObject("SELECT expires_at FROM reservation_process WHERE reservation_process_id = 'process-1'", Timestamp.class)
                .toInstant()).isEqualTo(Instant.parse("2027-03-01T10:00:00Z"));
    }

    @Test
    void la_expiracion_y_el_proceso_invalido_cierran_el_proceso() {
        proceso("process-1", "hold-1", "PHONE_SUBMITTED");
        proceso("process-2", "hold-2", "WAITING_FOR_PHONE");

        publica(resultado("AppointmentProcessExpired", "evt-1", "process-1", "hold-1", "\"reason\":\"HOLD_EXPIRED\""));
        publica(resultado("AppointmentProcessInvalid", "evt-2", "process-2", "hold-2", "\"reason\":\"REQUEST_EVENT_MISMATCH\""));
        esperaAlMarcador();

        assertThat(estado("process-1")).isEqualTo("EXPIRED");
        assertThat(estado("process-2")).isEqualTo("FAILED");
    }

    @Test
    void un_resultado_repetido_o_tardio_no_cambia_nada() {
        proceso("process-1", "hold-1", "PHONE_SUBMITTED");

        String confirmacion = resultado("AppointmentConfirmed", "evt-1", "process-1", "hold-1", "\"reservationId\":8251");
        publica(confirmacion);
        publica(confirmacion);
        // llega tarde una expiración y un rechazo: la reserva confirmada no se mueve
        publica(resultado("AppointmentProcessExpired", "evt-2", "process-1", "hold-1", "\"reason\":\"HOLD_EXPIRED\""));
        publica(resultado("AdditionalInformationRejected", "evt-3", "process-1", "hold-1", "\"reason\":\"INVALID_PHONE_NUMBER\""));
        esperaAlMarcador();

        assertThat(estado("process-1")).isEqualTo("CONFIRMED");
        // una sola escritura: la versión pasó de 0 a 1 y no más
        assertThat(jdbc.queryForObject("SELECT version FROM reservation_process WHERE reservation_process_id = 'process-1'", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id IN ('evt-1','evt-2','evt-3')", Integer.class)).isEqualTo(3);
    }

    // ---------- ayudas ----------

    private void publica(String mensaje) {
        kafka.send(TOPIC, "process-1", mensaje);
    }

    /** Publica un pedido válido de otro proceso y espera a que se aplique: los mensajes anteriores ya se consumieron. */
    private void esperaAlMarcador() {
        String marcador = "marcador-" + System.nanoTime();
        proceso(marcador, "hold-" + marcador, "HELD");
        kafka.send(TOPIC, marcador, pedido("evt-" + marcador, marcador, "hold-" + marcador));
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .until(() -> "WAITING_FOR_PHONE".equals(estado(marcador)));
    }

    private static String pedido(String eventId, String process, String hold) {
        return """
                {"eventId":"%s","eventType":"AdditionalInformationRequested","reservationProcessId":"%s","holdId":"%s",
                 "requiredField":"PHONE_NUMBER","message":"Ingrese un numero de telefono para continuar.",
                 "expiresAt":"2026-09-10T14:44:50Z","occurredAt":"2026-09-10T14:35:10Z","schemaVersion":1}"""
                .formatted(eventId, process, hold);
    }

    private static String resultado(String tipo, String eventId, String process, String hold, String campos) {
        return """
                {"eventId":"%s","eventType":"%s","groupId":"proyecto-juan-perez","reservationProcessId":"%s","holdId":"%s",
                 "requestEventId":"request-event-1",%s,"occurredAt":"2026-09-10T14:36:01Z","schemaVersion":1}"""
                .formatted(eventId, tipo, process, hold, campos);
    }

    private void proceso(String processId, String holdId, String estado) {
        jdbc.update("""
                INSERT INTO reservation_process (user_id, reservation_process_id, hold_id, professional_id, slot_date,
                    slot_start_time, external_patient_id, status, expires_at)
                VALUES (?, ?, ?, 15, DATE '2027-03-01', TIME '09:30', ?, ?, ?)""",
                userId, processId, holdId, "user-" + userId, estado, Timestamp.from(Instant.now().plusSeconds(600)));
    }

    private String estado(String processId) {
        return jdbc.queryForObject("SELECT status FROM reservation_process WHERE reservation_process_id = ?", String.class, processId);
    }

    private String eventId(String processId) {
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT phone_request_event_id FROM reservation_process WHERE reservation_process_id = ?", processId);
        return (String) fila.get("phone_request_event_id");
    }
}
