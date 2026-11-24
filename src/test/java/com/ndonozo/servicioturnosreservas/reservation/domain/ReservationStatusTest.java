package com.ndonozo.servicioturnosreservas.reservation.domain;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

/** La tabla de transiciones del ADR-0008, completa: cada par de estados, permitido o no. */
class ReservationStatusTest {

    private static final Map<ReservationStatus, Set<ReservationStatus>> PERMITIDAS = Map.of(
            HELD, EnumSet.of(WAITING_FOR_PHONE, EXPIRED, FAILED),
            WAITING_FOR_PHONE, EnumSet.of(PHONE_SUBMITTED, EXPIRED, FAILED),
            PHONE_SUBMITTED, EnumSet.of(CONFIRMED, WAITING_FOR_PHONE, EXPIRED, FAILED),
            CONFIRMED, EnumSet.of(CANCELLED),
            CANCELLED, EnumSet.noneOf(ReservationStatus.class),
            EXPIRED, EnumSet.noneOf(ReservationStatus.class),
            FAILED, EnumSet.noneOf(ReservationStatus.class));

    @Test
    void la_tabla_cubre_todos_los_estados() {
        assertThat(PERMITIDAS.keySet()).containsExactlyInAnyOrder(ReservationStatus.values());
    }

    @Test
    void solo_se_permiten_las_transiciones_de_la_tabla() {
        for (ReservationStatus desde : ReservationStatus.values()) {
            for (ReservationStatus hasta : ReservationStatus.values()) {
                assertThat(desde.canTransitionTo(hasta))
                        .as("%s -> %s", desde, hasta)
                        .isEqualTo(PERMITIDAS.get(desde).contains(hasta));
            }
        }
    }

    @Test
    void los_finales_son_cancelada_vencida_y_fallida() {
        assertThat(EnumSet.allOf(ReservationStatus.class).stream().filter(ReservationStatus::isFinal))
                .containsExactlyInAnyOrder(CANCELLED, EXPIRED, FAILED);
    }

    @Test
    void desde_un_estado_final_no_se_sale_a_ninguno() {
        for (ReservationStatus final_ : EnumSet.of(CANCELLED, EXPIRED, FAILED)) {
            for (ReservationStatus hasta : ReservationStatus.values()) {
                assertThat(final_.canTransitionTo(hasta)).as("%s -> %s", final_, hasta).isFalse();
            }
        }
    }

    @Test
    void una_reserva_confirmada_solo_se_puede_cancelar() {
        // un AppointmentProcessExpired o Invalid tardío no la altera (E §7, I §18.3)
        assertThat(CONFIRMED.isFinal()).isFalse();
        assertThat(CONFIRMED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(CONFIRMED.canTransitionTo(EXPIRED)).isFalse();
        assertThat(CONFIRMED.canTransitionTo(FAILED)).isFalse();
    }

    @Test
    void ningun_estado_avanza_a_si_mismo() {
        for (ReservationStatus estado : ReservationStatus.values()) {
            assertThat(estado.canTransitionTo(estado)).as(estado.name()).isFalse();
        }
    }
}
