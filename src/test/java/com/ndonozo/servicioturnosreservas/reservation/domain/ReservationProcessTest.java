package com.ndonozo.servicioturnosreservas.reservation.domain;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.ExternalPatientId;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationProcessTest {

    private static final Instant CREADO = Instant.parse("2026-11-24T12:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-11-24T12:05:00Z");

    private static ReservationProcess proceso(ReservationStatus estado) {
        return ReservationProcess.builder().id(1L).userId(7).reservationProcessId("process-1").holdId("hold-1")
                .professionalId(15).date(LocalDate.of(2026, 11, 30)).startTime(LocalTime.of(9, 30))
                .externalPatientId("user-7").status(estado).expiresAt(Instant.parse("2026-11-24T12:10:00Z"))
                .createdDate(CREADO).lastModifiedDate(CREADO).build();
    }

    @Test
    void una_transicion_permitida_cambia_el_estado_y_la_fecha_de_modificacion_y_nada_mas() {
        ReservationProcess nuevo = proceso(ReservationStatus.HELD).transitionTo(ReservationStatus.WAITING_FOR_PHONE, AHORA);

        assertThat(nuevo.status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);
        assertThat(nuevo.lastModifiedDate()).isEqualTo(AHORA);
        assertThat(nuevo).usingRecursiveComparison()
                .ignoringFields("status", "lastModifiedDate")
                .isEqualTo(proceso(ReservationStatus.HELD));
    }

    @Test
    void el_proceso_original_no_cambia() {
        ReservationProcess original = proceso(ReservationStatus.HELD);

        original.transitionTo(ReservationStatus.EXPIRED, AHORA);

        assertThat(original.status()).isEqualTo(ReservationStatus.HELD);
        assertThat(original.lastModifiedDate()).isEqualTo(CREADO);
    }

    @Test
    void una_transicion_prohibida_falla_y_dice_cual() {
        assertThatThrownBy(() -> proceso(ReservationStatus.CONFIRMED).transitionTo(ReservationStatus.EXPIRED, AHORA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIRMED -> EXPIRED");
    }

    @Test
    void un_proceso_en_estado_final_no_se_puede_reabrir() {
        assertThatThrownBy(() -> proceso(ReservationStatus.EXPIRED).transitionTo(ReservationStatus.WAITING_FOR_PHONE, AHORA))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void el_rechazo_del_telefono_vuelve_a_pedirlo() {
        ReservationProcess nuevo = proceso(ReservationStatus.PHONE_SUBMITTED)
                .transitionTo(ReservationStatus.WAITING_FOR_PHONE, AHORA);

        assertThat(nuevo.status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);
    }

    @Test
    void el_external_patient_id_es_el_id_interno_con_prefijo() {
        assertThat(ExternalPatientId.of(42)).isEqualTo("user-42");
    }

    @Test
    void el_external_patient_id_entra_en_los_100_caracteres_que_admite_la_catedra() {
        assertThat(ExternalPatientId.of(Long.MAX_VALUE).length()).isLessThanOrEqualTo(100);
    }
}
