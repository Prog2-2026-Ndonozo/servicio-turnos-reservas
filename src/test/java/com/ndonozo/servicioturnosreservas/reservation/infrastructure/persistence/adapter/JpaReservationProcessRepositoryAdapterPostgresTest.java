package com.ndonozo.servicioturnosreservas.reservation.infrastructure.persistence.adapter;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationStatus;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.persistence.mapper.ReservationProcessMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba contra un PostgreSQL real: Flyway aplica V1 y V2 y Hibernate corre en ddl-auto=validate, así que también comprueba
 * que la entidad calza con la tabla. Cada test se revierte al terminar.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({JpaReservationProcessRepositoryAdapter.class, ReservationProcessMapper.class})
class JpaReservationProcessRepositoryAdapterPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final Instant FECHA = Instant.parse("2026-11-24T12:00:00Z");
    private static final Instant VENCE = Instant.parse("2026-11-24T12:10:00Z");

    @Autowired
    private JpaReservationProcessRepositoryAdapter adapter;

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    private long userId;

    @BeforeEach
    void hayUnUsuario() {
        userId = jdbc.queryForObject("""
                INSERT INTO app_user (login, password_hash, email, lang_key)
                VALUES ('ana', repeat('x', 60), 'ana@correo.com', 'es') RETURNING id""", Long.class);
    }

    private ReservationProcess proceso(String processId, String holdId) {
        return ReservationProcess.builder().userId(userId).reservationProcessId(processId).holdId(holdId)
                .professionalId(15).date(LocalDate.of(2026, 11, 30)).startTime(LocalTime.of(9, 30))
                .externalPatientId("user-" + userId).status(ReservationStatus.HELD).expiresAt(VENCE)
                .createdDate(FECHA).lastModifiedDate(FECHA).build();
    }

    @Test
    void save_asigna_el_id_y_lo_guardado_se_lee_completo_desde_la_base() {
        ReservationProcess guardado = adapter.save(proceso("process-1", "hold-1"));
        em.clear();

        assertThat(guardado.id()).isNotNull();
        var fila = jdbc.queryForMap("SELECT * FROM reservation_process WHERE id = ?", guardado.id());
        assertThat(fila).containsEntry("user_id", userId)
                .containsEntry("reservation_process_id", "process-1").containsEntry("hold_id", "hold-1")
                .containsEntry("professional_id", 15L).containsEntry("status", "HELD")
                .containsEntry("external_patient_id", "user-" + userId);
        assertThat(fila.get("slot_date").toString()).isEqualTo("2026-11-30");
        assertThat(fila.get("slot_start_time").toString()).isEqualTo("09:30:00");
    }

    @Test
    void el_instante_de_vencimiento_se_guarda_sin_perder_precision() {
        ReservationProcess guardado = adapter.save(proceso("process-1", "hold-1"));
        em.clear();

        ReservationProcess leido = adapter.save(guardado.toBuilder().lastModifiedDate(FECHA.plusSeconds(1)).build());

        assertThat(leido.expiresAt()).isEqualTo(VENCE);
        assertThat(leido.createdDate()).isEqualTo(FECHA);
        assertThat(leido.status()).isEqualTo(ReservationStatus.HELD);
    }

    @Test
    void no_se_guardan_dos_procesos_con_el_mismo_reservation_process_id() {
        adapter.save(proceso("process-1", "hold-1"));

        assertThatThrownBy(() -> adapter.save(proceso("process-1", "hold-2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void no_se_guardan_dos_procesos_con_el_mismo_hold_id() {
        adapter.save(proceso("process-1", "hold-1"));

        assertThatThrownBy(() -> adapter.save(proceso("process-2", "hold-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void el_proceso_tiene_que_pertenecer_a_un_usuario_que_existe() {
        ReservationProcess deNadie = proceso("process-1", "hold-1").toBuilder().userId(987654).build();

        assertThatThrownBy(() -> adapter.save(deNadie)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void la_base_rechaza_un_estado_que_la_maquina_no_conoce() {
        adapter.save(proceso("process-1", "hold-1"));

        assertThatThrownBy(() -> jdbc.update("UPDATE reservation_process SET status = 'INVENTADO'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void busca_el_proceso_por_su_id_solo_si_es_del_usuario() {
        adapter.save(proceso("process-1", "hold-1"));
        em.clear();

        assertThat(adapter.findByReservationProcessIdAndUserId("process-1", userId)).get()
                .satisfies(p -> {
                    assertThat(p.holdId()).isEqualTo("hold-1");
                    assertThat(p.status()).isEqualTo(ReservationStatus.HELD);
                });
        assertThat(adapter.findByReservationProcessIdAndUserId("process-1", userId + 1)).isEmpty();
        assertThat(adapter.findByReservationProcessIdAndUserId("no-existe", userId)).isEmpty();
    }

    @Test
    void guardar_un_proceso_ya_existente_actualiza_su_fila_y_no_crea_otra() {
        ReservationProcess guardado = adapter.save(proceso("process-1", "hold-1"));
        em.clear();

        ReservationProcess esperando = adapter.save(guardado.transitionTo(ReservationStatus.WAITING_FOR_PHONE, FECHA.plusSeconds(30)));
        em.clear();

        assertThat(esperando.id()).isEqualTo(guardado.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation_process", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM reservation_process", String.class)).isEqualTo("WAITING_FOR_PHONE");
        assertThat(adapter.findByReservationProcessIdAndUserId("process-1", userId)).get()
                .satisfies(p -> {
                    assertThat(p.createdDate()).isEqualTo(FECHA);
                    assertThat(p.lastModifiedDate()).isEqualTo(FECHA.plusSeconds(30));
                });
    }
}
