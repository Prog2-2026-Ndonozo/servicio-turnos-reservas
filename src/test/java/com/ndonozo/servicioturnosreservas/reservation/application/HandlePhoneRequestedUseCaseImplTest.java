package com.ndonozo.servicioturnosreservas.reservation.application;

import com.ndonozo.servicioturnosreservas.reservation.application.usecases.HandlePhoneRequestedUseCaseImpl;
import com.ndonozo.servicioturnosreservas.reservation.domain.exception.ReservationConflictException;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneRequestResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneRequestedEvent;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandlePhoneRequestedUseCaseImplTest {

    private static final Instant AHORA = Instant.parse("2026-11-25T12:00:00Z");
    private static final PhoneRequestedEvent EVENTO = new PhoneRequestedEvent("evt-1", "process-1", "hold-1");

    @Mock
    private ProcessedEventRepository processedEvents;

    @Mock
    private ReservationProcessRepository repository;

    private HandlePhoneRequestedUseCaseImpl useCase() {
        return new HandlePhoneRequestedUseCaseImpl(processedEvents, repository, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static ReservationProcess proceso(ReservationStatus estado) {
        return ReservationProcess.builder().id(1L).userId(7).reservationProcessId("process-1").holdId("hold-1")
                .professionalId(15).date(LocalDate.of(2026, 11, 30)).startTime(LocalTime.of(9, 30))
                .externalPatientId("user-7").status(estado).expiresAt(AHORA.plusSeconds(600))
                .createdDate(AHORA.minusSeconds(60)).lastModifiedDate(AHORA.minusSeconds(60)).version(0L).build();
    }

    private void primeraVez(ReservationProcess proceso) {
        when(processedEvents.markProcessed("evt-1", AHORA)).thenReturn(true);
        when(repository.findByReservationProcessId("process-1")).thenReturn(Optional.of(proceso));
    }

    private void guardaLoRecibido() {
        when(repository.save(any(ReservationProcess.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void un_proceso_en_HELD_pasa_a_WAITING_FOR_PHONE_y_guarda_el_eventId() {
        primeraVez(proceso(ReservationStatus.HELD));
        guardaLoRecibido();

        PhoneRequestResult resultado = useCase().handlePhoneRequested(EVENTO);

        ArgumentCaptor<ReservationProcess> guardado = ArgumentCaptor.forClass(ReservationProcess.class);
        verify(repository).save(guardado.capture());
        assertThat(guardado.getValue().status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);
        assertThat(guardado.getValue().phoneRequestEventId()).isEqualTo("evt-1");
        assertThat(guardado.getValue().lastModifiedDate()).isEqualTo(AHORA);
        assertThat(resultado).isInstanceOf(PhoneRequestResult.Applied.class);
    }

    @Test
    void un_proceso_que_ya_esperaba_el_telefono_solo_guarda_el_eventId_nuevo() {
        primeraVez(proceso(ReservationStatus.WAITING_FOR_PHONE).toBuilder().phoneRequestEventId("evt-viejo").build());
        guardaLoRecibido();

        assertThat(useCase().handlePhoneRequested(EVENTO)).isInstanceOfSatisfying(PhoneRequestResult.Applied.class, a -> {
            assertThat(a.process().status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);
            assertThat(a.process().phoneRequestEventId()).isEqualTo("evt-1");
        });
    }

    @Test
    void un_evento_repetido_no_toca_nada() {
        when(processedEvents.markProcessed("evt-1", AHORA)).thenReturn(false);

        assertThat(useCase().handlePhoneRequested(EVENTO)).isInstanceOf(PhoneRequestResult.Duplicate.class);

        verifyNoInteractions(repository);
    }

    @Test
    void un_proceso_desconocido_se_informa_y_el_evento_queda_registrado() {
        when(processedEvents.markProcessed("evt-1", AHORA)).thenReturn(true);
        when(repository.findByReservationProcessId("process-1")).thenReturn(Optional.empty());

        assertThat(useCase().handlePhoneRequested(EVENTO)).isInstanceOf(PhoneRequestResult.ProcessNotFound.class);

        verify(repository, never()).save(any());
    }

    @Test
    void un_hold_distinto_no_cambia_el_proceso() {
        primeraVez(proceso(ReservationStatus.HELD));

        assertThat(useCase().handlePhoneRequested(new PhoneRequestedEvent("evt-1", "process-1", "otro-hold")))
                .isInstanceOf(PhoneRequestResult.HoldMismatch.class);

        verify(repository, never()).save(any());
    }

    @Test
    void en_cualquier_otro_estado_el_evento_no_cambia_nada_ni_retrocede_un_proceso_final() {
        for (ReservationStatus estado : new ReservationStatus[]{ReservationStatus.PHONE_SUBMITTED,
                ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED, ReservationStatus.EXPIRED, ReservationStatus.FAILED}) {
            when(processedEvents.markProcessed("evt-1", AHORA)).thenReturn(true);
            when(repository.findByReservationProcessId("process-1")).thenReturn(Optional.of(proceso(estado)));

            assertThat(useCase().handlePhoneRequested(EVENTO)).isInstanceOf(PhoneRequestResult.NotApplicable.class);
        }
        verify(repository, never()).save(any());
    }

    @Test
    void un_choque_de_version_sale_para_que_la_transaccion_se_revierta_y_Kafka_reintente() {
        primeraVez(proceso(ReservationStatus.HELD));
        when(repository.save(any(ReservationProcess.class))).thenThrow(new ReservationConflictException("choque", null));

        assertThatThrownBy(() -> useCase().handlePhoneRequested(EVENTO)).isInstanceOf(ReservationConflictException.class);
    }
}
