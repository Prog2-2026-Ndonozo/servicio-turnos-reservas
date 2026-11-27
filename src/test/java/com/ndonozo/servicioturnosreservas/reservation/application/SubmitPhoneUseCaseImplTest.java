package com.ndonozo.servicioturnosreservas.reservation.application;

import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.application.usecases.SubmitPhoneUseCaseImpl;
import com.ndonozo.servicioturnosreservas.reservation.domain.exception.ReservationConflictException;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneSubmission;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationStatus;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.SubmitPhoneCommand;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.SubmitPhoneResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.PhoneSubmissionPort;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.ReservationProcessRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmitPhoneUseCaseImplTest {

    private static final Instant AHORA = Instant.parse("2026-11-27T12:00:00Z");
    private static final Instant VENCE = Instant.parse("2026-11-27T12:10:00Z");
    private static final SubmitPhoneCommand COMANDO = new SubmitPhoneCommand(7, "process-1", "+54 9 261 555-1234");

    @Mock
    private ReservationProcessRepository repository;

    @Mock
    private PhoneSubmissionPort phonePort;

    private SubmitPhoneUseCaseImpl useCase() {
        return new SubmitPhoneUseCaseImpl(repository, phonePort, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static ReservationProcess proceso(ReservationStatus estado, Instant vence, String requestEventId) {
        return ReservationProcess.builder().id(1L).userId(7).reservationProcessId("process-1").holdId("hold-1")
                .professionalId(15).date(LocalDate.of(2026, 11, 30)).startTime(LocalTime.of(9, 30))
                .externalPatientId("user-7").status(estado).expiresAt(vence).phoneRequestEventId(requestEventId)
                .createdDate(AHORA.minusSeconds(60)).lastModifiedDate(AHORA.minusSeconds(60)).version(2L).build();
    }

    private void existe(ReservationProcess proceso) {
        when(repository.findByReservationProcessIdAndUserId("process-1", 7)).thenReturn(Optional.of(proceso));
    }

    private void guardaTalCual() {
        when(repository.save(any(ReservationProcess.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void guarda_PHONE_SUBMITTED_y_despues_publica_el_telefono_normalizado_con_el_requestEventId_guardado() {
        existe(proceso(ReservationStatus.WAITING_FOR_PHONE, VENCE, "request-event-1"));
        guardaTalCual();

        SubmitPhoneResult resultado = useCase().submitPhone(COMANDO);

        InOrder orden = inOrder(repository, phonePort);
        ArgumentCaptor<ReservationProcess> guardado = ArgumentCaptor.forClass(ReservationProcess.class);
        orden.verify(repository).save(guardado.capture());
        ArgumentCaptor<PhoneSubmission> enviado = ArgumentCaptor.forClass(PhoneSubmission.class);
        orden.verify(phonePort).submit(enviado.capture());

        assertThat(guardado.getValue().status()).isEqualTo(ReservationStatus.PHONE_SUBMITTED);
        assertThat(guardado.getValue().lastModifiedDate()).isEqualTo(AHORA);
        assertThat(guardado.getValue().phoneRequestEventId()).isEqualTo("request-event-1");
        assertThat(enviado.getValue().eventId()).startsWith("phone-submitted-");
        assertThat(enviado.getValue().reservationProcessId()).isEqualTo("process-1");
        assertThat(enviado.getValue().holdId()).isEqualTo("hold-1");
        assertThat(enviado.getValue().requestEventId()).isEqualTo("request-event-1");
        assertThat(enviado.getValue().phoneNumber()).isEqualTo("+5492615551234");
        assertThat(enviado.getValue().occurredAt()).isEqualTo(AHORA);
        assertThat(resultado).isInstanceOfSatisfying(SubmitPhoneResult.Submitted.class,
                s -> assertThat(s.process().status()).isEqualTo(ReservationStatus.PHONE_SUBMITTED));
    }

    @Test
    void cada_envio_usa_un_eventId_nuevo() {
        existe(proceso(ReservationStatus.WAITING_FOR_PHONE, VENCE, "request-event-1"));
        guardaTalCual();

        useCase().submitPhone(COMANDO);
        useCase().submitPhone(COMANDO);

        ArgumentCaptor<PhoneSubmission> enviado = ArgumentCaptor.forClass(PhoneSubmission.class);
        verify(phonePort, times(2)).submit(enviado.capture());
        assertThat(enviado.getAllValues().get(0).eventId()).isNotEqualTo(enviado.getAllValues().get(1).eventId());
    }

    @Test
    void un_telefono_invalido_no_toca_nada() {
        assertThat(useCase().submitPhone(new SubmitPhoneCommand(7, "process-1", "12345")))
                .isInstanceOf(SubmitPhoneResult.InvalidPhone.class);

        verifyNoInteractions(repository, phonePort);
    }

    @Test
    void un_proceso_ajeno_o_inexistente_es_NotFound() {
        when(repository.findByReservationProcessIdAndUserId("process-1", 7)).thenReturn(Optional.empty());

        assertThat(useCase().submitPhone(COMANDO)).isInstanceOf(SubmitPhoneResult.NotFound.class);

        verifyNoInteractions(phonePort);
        verify(repository, never()).save(any());
    }

    @Test
    void solo_WAITING_FOR_PHONE_puede_enviar() {
        for (ReservationStatus estado : ReservationStatus.values()) {
            if (estado == ReservationStatus.WAITING_FOR_PHONE) {
                continue;
            }
            existe(proceso(estado, VENCE, "request-event-1"));
            assertThat(useCase().submitPhone(COMANDO)).as(estado.name()).isInstanceOf(SubmitPhoneResult.InvalidState.class);
        }
        verifyNoInteractions(phonePort);
        verify(repository, never()).save(any());
    }

    @Test
    void pasado_expiresAt_lo_cierra_como_EXPIRED_sin_publicar() {
        existe(proceso(ReservationStatus.WAITING_FOR_PHONE, AHORA, "request-event-1"));
        guardaTalCual();

        assertThat(useCase().submitPhone(COMANDO)).isInstanceOf(SubmitPhoneResult.HoldExpired.class);

        ArgumentCaptor<ReservationProcess> guardado = ArgumentCaptor.forClass(ReservationProcess.class);
        verify(repository).save(guardado.capture());
        assertThat(guardado.getValue().status()).isEqualTo(ReservationStatus.EXPIRED);
        verifyNoInteractions(phonePort);
    }

    @Test
    void sin_pedido_de_telefono_recibido_no_publica_ni_cambia_el_estado() {
        existe(proceso(ReservationStatus.WAITING_FOR_PHONE, VENCE, null));

        assertThat(useCase().submitPhone(COMANDO)).isInstanceOf(SubmitPhoneResult.PhoneNotRequested.class);

        verifyNoInteractions(phonePort);
        verify(repository, never()).save(any());
    }

    @Test
    void si_no_se_pudo_publicar_el_proceso_vuelve_a_WAITING_FOR_PHONE_y_el_error_sale() {
        existe(proceso(ReservationStatus.WAITING_FOR_PHONE, VENCE, "request-event-1"));
        guardaTalCual();
        doThrow(new CatedraUnavailableException("sin broker")).when(phonePort).submit(any());

        assertThatThrownBy(() -> useCase().submitPhone(COMANDO)).isInstanceOf(CatedraUnavailableException.class);

        ArgumentCaptor<ReservationProcess> guardado = ArgumentCaptor.forClass(ReservationProcess.class);
        verify(repository, times(2)).save(guardado.capture());
        assertThat(guardado.getAllValues()).extracting(ReservationProcess::status)
                .containsExactly(ReservationStatus.PHONE_SUBMITTED, ReservationStatus.WAITING_FOR_PHONE);
    }

    @Test
    void si_tampoco_se_puede_deshacer_sale_el_error_original_con_el_otro_como_suprimido() {
        existe(proceso(ReservationStatus.WAITING_FOR_PHONE, VENCE, "request-event-1"));
        ReservationConflictException conflicto = new ReservationConflictException("choque", null);
        when(repository.save(any(ReservationProcess.class))).thenAnswer(i -> i.getArgument(0)).thenThrow(conflicto);
        CatedraUnavailableException original = new CatedraUnavailableException("sin broker");
        doThrow(original).when(phonePort).submit(any());

        assertThatThrownBy(() -> useCase().submitPhone(COMANDO)).isSameAs(original).hasSuppressedException(conflicto);
    }

    @Test
    void si_otro_pedido_ya_guardo_el_proceso_el_choque_sale_y_no_se_publica() {
        existe(proceso(ReservationStatus.WAITING_FOR_PHONE, VENCE, "request-event-1"));
        when(repository.save(any(ReservationProcess.class))).thenThrow(new ReservationConflictException("choque", null));

        assertThatThrownBy(() -> useCase().submitPhone(COMANDO)).isInstanceOf(ReservationConflictException.class);

        verifyNoInteractions(phonePort);
    }
}
