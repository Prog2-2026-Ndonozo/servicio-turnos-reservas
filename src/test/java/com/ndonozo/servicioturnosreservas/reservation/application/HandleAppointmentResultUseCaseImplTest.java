package com.ndonozo.servicioturnosreservas.reservation.application;

import com.ndonozo.servicioturnosreservas.reservation.application.usecases.HandleAppointmentResultUseCaseImpl;
import com.ndonozo.servicioturnosreservas.reservation.domain.exception.ReservationConflictException;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.AppointmentResultEvent;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.AppointmentResultOutcome;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationStatus;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.ProcessedEventRepository;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.ReservationProcessRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** La tabla de reglas de ADR-0011: qué hace cada resultado de la cátedra según el estado del proceso. */
@ExtendWith(MockitoExtension.class)
class HandleAppointmentResultUseCaseImplTest {

    private static final Instant AHORA = Instant.parse("2026-11-27T12:00:00Z");
    private static final Instant VENCE = Instant.parse("2026-11-27T12:10:00Z");
    private static final Instant NUEVO_VENCIMIENTO = Instant.parse("2026-11-27T12:20:00Z");

    private static final AppointmentResultEvent CONFIRMADO = new AppointmentResultEvent.Confirmed("evt-1", "process-1", "hold-1", 8251L);
    private static final AppointmentResultEvent RECHAZADO =
            new AppointmentResultEvent.Rejected("evt-1", "process-1", "hold-1", "INVALID_PHONE_NUMBER", NUEVO_VENCIMIENTO);
    private static final AppointmentResultEvent VENCIDO = new AppointmentResultEvent.Expired("evt-1", "process-1", "hold-1");
    private static final AppointmentResultEvent INVALIDO = new AppointmentResultEvent.Invalid("evt-1", "process-1", "hold-1");

    @Mock
    private ProcessedEventRepository processedEvents;

    @Mock
    private ReservationProcessRepository repository;

    private HandleAppointmentResultUseCaseImpl useCase() {
        return new HandleAppointmentResultUseCaseImpl(processedEvents, repository, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static ReservationProcess proceso(ReservationStatus estado) {
        return ReservationProcess.builder().id(1L).userId(7).reservationProcessId("process-1").holdId("hold-1")
                .professionalId(15).date(LocalDate.of(2026, 11, 30)).startTime(LocalTime.of(9, 30))
                .externalPatientId("user-7").status(estado).expiresAt(VENCE).phoneRequestEventId("request-event-1")
                .createdDate(AHORA.minusSeconds(60)).lastModifiedDate(AHORA.minusSeconds(60)).version(3L).build();
    }

    private void primeraVez(ReservationProcess proceso) {
        when(processedEvents.markProcessed("evt-1", AHORA)).thenReturn(true);
        when(repository.findByReservationProcessId("process-1")).thenReturn(Optional.of(proceso));
    }

    private void guardaLoRecibido() {
        when(repository.save(any(ReservationProcess.class))).thenAnswer(i -> i.getArgument(0));
    }

    private ReservationProcess guardado() {
        ArgumentCaptor<ReservationProcess> captor = ArgumentCaptor.forClass(ReservationProcess.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void la_confirmacion_pasa_PHONE_SUBMITTED_a_CONFIRMED_y_guarda_el_reservationId() {
        primeraVez(proceso(ReservationStatus.PHONE_SUBMITTED));
        guardaLoRecibido();

        AppointmentResultOutcome resultado = useCase().handleResult(CONFIRMADO);

        assertThat(guardado().status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(guardado().reservationId()).isEqualTo(8251L);
        assertThat(guardado().lastModifiedDate()).isEqualTo(AHORA);
        assertThat(resultado).isInstanceOf(AppointmentResultOutcome.Applied.class);
    }

    @Test
    void el_rechazo_vuelve_a_WAITING_FOR_PHONE_con_el_motivo_el_vencimiento_nuevo_y_el_pedido_original() {
        primeraVez(proceso(ReservationStatus.PHONE_SUBMITTED));
        guardaLoRecibido();

        useCase().handleResult(RECHAZADO);

        ReservationProcess p = guardado();
        assertThat(p.status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);
        assertThat(p.rejectionReason()).isEqualTo("INVALID_PHONE_NUMBER");
        assertThat(p.expiresAt()).isEqualTo(NUEVO_VENCIMIENTO);
        assertThat(p.phoneRequestEventId()).isEqualTo("request-event-1");
    }

    @Test
    void el_rechazo_sin_vencimiento_conserva_el_anterior() {
        primeraVez(proceso(ReservationStatus.PHONE_SUBMITTED));
        guardaLoRecibido();

        useCase().handleResult(new AppointmentResultEvent.Rejected("evt-1", "process-1", "hold-1", null, null));

        assertThat(guardado().expiresAt()).isEqualTo(VENCE);
    }

    @Test
    void la_expiracion_cierra_los_procesos_abiertos_como_EXPIRED() {
        for (ReservationStatus estado : Set.of(ReservationStatus.HELD, ReservationStatus.WAITING_FOR_PHONE, ReservationStatus.PHONE_SUBMITTED)) {
            org.mockito.Mockito.reset(repository, processedEvents);
            primeraVez(proceso(estado));
            guardaLoRecibido();

            AppointmentResultOutcome resultado = useCase().handleResult(VENCIDO);

            assertThat(guardado().status()).as(estado.name()).isEqualTo(ReservationStatus.EXPIRED);
            assertThat(resultado).isInstanceOf(AppointmentResultOutcome.Applied.class);
        }
    }

    @Test
    void el_proceso_invalido_cierra_los_procesos_abiertos_como_FAILED() {
        for (ReservationStatus estado : Set.of(ReservationStatus.HELD, ReservationStatus.WAITING_FOR_PHONE, ReservationStatus.PHONE_SUBMITTED)) {
            org.mockito.Mockito.reset(repository, processedEvents);
            primeraVez(proceso(estado));
            guardaLoRecibido();

            AppointmentResultOutcome resultado = useCase().handleResult(INVALIDO);

            assertThat(guardado().status()).as(estado.name()).isEqualTo(ReservationStatus.FAILED);
            assertThat(resultado).isInstanceOf(AppointmentResultOutcome.Applied.class);
        }
    }

    @Test
    void confirmacion_y_rechazo_solo_actuan_sobre_PHONE_SUBMITTED() {
        Set<ReservationStatus> otros = EnumSet.complementOf(EnumSet.of(ReservationStatus.PHONE_SUBMITTED));
        for (ReservationStatus estado : otros) {
            for (AppointmentResultEvent evento : new AppointmentResultEvent[]{CONFIRMADO, RECHAZADO}) {
                org.mockito.Mockito.reset(repository, processedEvents);
                primeraVez(proceso(estado));

                assertThat(useCase().handleResult(evento)).as(estado + " " + evento.getClass().getSimpleName())
                        .isInstanceOf(AppointmentResultOutcome.NotApplicable.class);
                verify(repository, never()).save(any());
            }
        }
    }

    @Test
    void un_estado_final_o_confirmado_no_se_altera_con_ningun_resultado() {
        for (ReservationStatus estado : Set.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED,
                ReservationStatus.EXPIRED, ReservationStatus.FAILED)) {
            for (AppointmentResultEvent evento : new AppointmentResultEvent[]{CONFIRMADO, RECHAZADO, VENCIDO, INVALIDO}) {
                org.mockito.Mockito.reset(repository, processedEvents);
                primeraVez(proceso(estado));

                assertThat(useCase().handleResult(evento)).as(estado + " " + evento.getClass().getSimpleName())
                        .isInstanceOf(AppointmentResultOutcome.NotApplicable.class);
                verify(repository, never()).save(any());
            }
        }
    }

    @Test
    void un_evento_ya_procesado_es_duplicado_y_no_toca_nada() {
        when(processedEvents.markProcessed("evt-1", AHORA)).thenReturn(false);

        assertThat(useCase().handleResult(CONFIRMADO)).isInstanceOf(AppointmentResultOutcome.Duplicate.class);

        verifyNoInteractions(repository);
    }

    @Test
    void un_proceso_desconocido_se_descarta() {
        when(processedEvents.markProcessed("evt-1", AHORA)).thenReturn(true);
        when(repository.findByReservationProcessId("process-1")).thenReturn(Optional.empty());

        assertThat(useCase().handleResult(CONFIRMADO)).isInstanceOf(AppointmentResultOutcome.ProcessNotFound.class);
    }

    @Test
    void un_holdId_distinto_se_descarta_sin_cambiar_nada() {
        primeraVez(proceso(ReservationStatus.PHONE_SUBMITTED));

        assertThat(useCase().handleResult(new AppointmentResultEvent.Confirmed("evt-1", "process-1", "otro-hold", 1L)))
                .isInstanceOf(AppointmentResultOutcome.HoldMismatch.class);
        verify(repository, never()).save(any());
    }

    @Test
    void un_choque_de_version_sale_para_que_Spring_Kafka_reintente() {
        primeraVez(proceso(ReservationStatus.PHONE_SUBMITTED));
        when(repository.save(any(ReservationProcess.class))).thenThrow(new ReservationConflictException("choque", null));

        assertThatThrownBy(() -> useCase().handleResult(CONFIRMADO)).isInstanceOf(ReservationConflictException.class);
    }
}
