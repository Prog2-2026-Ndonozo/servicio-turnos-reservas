package com.ndonozo.servicioturnosreservas.reservation.application;

import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.application.usecases.ConfirmReservationUseCaseImpl;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ConfirmHoldResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ConfirmReservationCommand;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ConfirmReservationResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationStatus;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.HoldPort;
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
class ConfirmReservationUseCaseImplTest {

    private static final Instant AHORA = Instant.parse("2026-11-24T12:00:00Z");
    private static final Instant VENCE = Instant.parse("2026-11-24T12:10:00Z");
    private static final ConfirmReservationCommand COMANDO = new ConfirmReservationCommand(7, "process-1", "Juan", "Perez");

    @Mock
    private ReservationProcessRepository repository;

    @Mock
    private HoldPort holdPort;

    private ConfirmReservationUseCaseImpl useCase() {
        return new ConfirmReservationUseCaseImpl(repository, holdPort, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static ReservationProcess proceso(ReservationStatus estado, Instant vence) {
        return ReservationProcess.builder().id(1L).userId(7).reservationProcessId("process-1").holdId("hold-1")
                .professionalId(15).date(LocalDate.of(2026, 11, 30)).startTime(LocalTime.of(9, 30))
                .externalPatientId("user-7").status(estado).expiresAt(vence)
                .createdDate(AHORA.minusSeconds(60)).lastModifiedDate(AHORA.minusSeconds(60)).build();
    }

    private void existe(ReservationProcess proceso) {
        when(repository.findByReservationProcessIdAndUserId("process-1", 7)).thenReturn(Optional.of(proceso));
        when(repository.save(any(ReservationProcess.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void confirma_en_la_catedra_con_los_datos_del_proceso_y_lo_pasa_a_WAITING_FOR_PHONE() {
        existe(proceso(ReservationStatus.HELD, VENCE));
        when(holdPort.confirmHold("hold-1", "user-7", "Juan", "Perez")).thenReturn(new ConfirmHoldResult.Confirmed());

        ConfirmReservationResult resultado = useCase().confirmReservation(COMANDO);

        ArgumentCaptor<ReservationProcess> guardado = ArgumentCaptor.forClass(ReservationProcess.class);
        verify(repository).save(guardado.capture());
        assertThat(guardado.getValue().status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);
        assertThat(guardado.getValue().lastModifiedDate()).isEqualTo(AHORA);
        assertThat(guardado.getValue().expiresAt()).isEqualTo(VENCE);
        assertThat(resultado).isInstanceOfSatisfying(ConfirmReservationResult.Confirmed.class,
                c -> assertThat(c.process().status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE));
    }

    @Test
    void si_la_catedra_dice_que_ya_estaba_confirmado_reconcilia_y_lo_pasa_a_WAITING_FOR_PHONE() {
        existe(proceso(ReservationStatus.HELD, VENCE));
        when(holdPort.confirmHold("hold-1", "user-7", "Juan", "Perez")).thenReturn(new ConfirmHoldResult.AlreadyConfirmed());

        assertThat(useCase().confirmReservation(COMANDO)).isInstanceOfSatisfying(ConfirmReservationResult.Confirmed.class,
                c -> assertThat(c.process().status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE));
    }

    @Test
    void un_proceso_que_ya_espera_el_telefono_responde_igual_sin_llamar_a_la_catedra() {
        ReservationProcess esperando = proceso(ReservationStatus.WAITING_FOR_PHONE, VENCE);
        when(repository.findByReservationProcessIdAndUserId("process-1", 7)).thenReturn(Optional.of(esperando));

        assertThat(useCase().confirmReservation(COMANDO)).isEqualTo(new ConfirmReservationResult.Confirmed(esperando));

        verifyNoInteractions(holdPort);
        verify(repository, never()).save(any());
    }

    @Test
    void un_proceso_inexistente_o_de_otro_usuario_es_NotFound_y_no_toca_la_catedra() {
        when(repository.findByReservationProcessIdAndUserId("process-1", 7)).thenReturn(Optional.empty());

        assertThat(useCase().confirmReservation(COMANDO)).isInstanceOf(ConfirmReservationResult.NotFound.class);

        verifyNoInteractions(holdPort);
    }

    @Test
    void un_proceso_en_otro_estado_no_se_puede_confirmar() {
        for (ReservationStatus estado : new ReservationStatus[]{ReservationStatus.PHONE_SUBMITTED,
                ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED, ReservationStatus.EXPIRED, ReservationStatus.FAILED}) {
            when(repository.findByReservationProcessIdAndUserId("process-1", 7)).thenReturn(Optional.of(proceso(estado, VENCE)));

            assertThat(useCase().confirmReservation(COMANDO)).isInstanceOf(ConfirmReservationResult.InvalidState.class);
        }
        verifyNoInteractions(holdPort);
        verify(repository, never()).save(any());
    }

    @Test
    void un_hold_cuyo_vencimiento_ya_paso_se_cierra_como_EXPIRED_sin_llamar_a_la_catedra() {
        existe(proceso(ReservationStatus.HELD, AHORA));

        assertThat(useCase().confirmReservation(COMANDO)).isInstanceOf(ConfirmReservationResult.HoldExpired.class);

        ArgumentCaptor<ReservationProcess> guardado = ArgumentCaptor.forClass(ReservationProcess.class);
        verify(repository).save(guardado.capture());
        assertThat(guardado.getValue().status()).isEqualTo(ReservationStatus.EXPIRED);
        verifyNoInteractions(holdPort);
    }

    @Test
    void si_la_catedra_dice_que_el_hold_vencio_el_proceso_pasa_a_EXPIRED() {
        existe(proceso(ReservationStatus.HELD, VENCE));
        when(holdPort.confirmHold("hold-1", "user-7", "Juan", "Perez")).thenReturn(new ConfirmHoldResult.HoldExpired());

        assertThat(useCase().confirmReservation(COMANDO)).isInstanceOf(ConfirmReservationResult.HoldExpired.class);

        ArgumentCaptor<ReservationProcess> guardado = ArgumentCaptor.forClass(ReservationProcess.class);
        verify(repository).save(guardado.capture());
        assertThat(guardado.getValue().status()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void si_otro_reservo_el_turno_se_informa_y_el_proceso_no_cambia() {
        when(repository.findByReservationProcessIdAndUserId("process-1", 7))
                .thenReturn(Optional.of(proceso(ReservationStatus.HELD, VENCE)));
        when(holdPort.confirmHold("hold-1", "user-7", "Juan", "Perez")).thenReturn(new ConfirmHoldResult.SlotAlreadyReserved());

        assertThat(useCase().confirmReservation(COMANDO)).isInstanceOf(ConfirmReservationResult.SlotAlreadyReserved.class);

        verify(repository, never()).save(any());
    }

    @Test
    void una_falla_de_comunicacion_con_la_catedra_sale_sin_cambiar_el_proceso() {
        when(repository.findByReservationProcessIdAndUserId("process-1", 7))
                .thenReturn(Optional.of(proceso(ReservationStatus.HELD, VENCE)));
        when(holdPort.confirmHold("hold-1", "user-7", "Juan", "Perez")).thenThrow(new CatedraUnavailableException("sin conexión"));

        assertThatThrownBy(() -> useCase().confirmReservation(COMANDO)).isInstanceOf(CatedraUnavailableException.class);

        verify(repository, never()).save(any());
    }
}
