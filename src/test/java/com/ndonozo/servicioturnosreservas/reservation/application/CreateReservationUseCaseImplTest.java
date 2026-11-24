package com.ndonozo.servicioturnosreservas.reservation.application;

import com.ndonozo.servicioturnosreservas.availability.domain.model.SlotCheckResult;
import com.ndonozo.servicioturnosreservas.availability.domain.ports.in.CheckSlotReservableUseCase;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.application.usecases.CreateReservationUseCaseImpl;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.CreateReservationCommand;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.CreateReservationResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.Hold;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.HoldResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationStatus;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.HoldPort;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateReservationUseCaseImplTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 11, 30);
    private static final LocalTime HORA = LocalTime.of(9, 30);
    private static final Instant AHORA = Instant.parse("2026-11-24T12:00:00Z");
    private static final Instant VENCE = Instant.parse("2026-11-24T12:10:00Z");
    private static final CreateReservationCommand COMANDO = new CreateReservationCommand(7, 15, FECHA, HORA);
    private static final Hold HOLD = new Hold("hold-1", "process-1", VENCE);

    @Mock
    private CheckSlotReservableUseCase slotChecker;

    @Mock
    private HoldPort holdPort;

    @Mock
    private ReservationProcessRepository repository;

    private CreateReservationUseCaseImpl useCase() {
        return new CreateReservationUseCaseImpl(slotChecker, holdPort, repository, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private void turnoReservable() {
        when(slotChecker.checkSlot(15, FECHA, HORA)).thenReturn(new SlotCheckResult.Reservable());
    }

    @Test
    void crea_el_hold_con_el_id_del_usuario_y_guarda_el_proceso_en_HELD() {
        turnoReservable();
        when(holdPort.createHold(15, FECHA, HORA, "user-7")).thenReturn(new HoldResult.Created(HOLD));
        when(repository.save(any())).thenAnswer(invocacion -> {
            ReservationProcess guardado = invocacion.getArgument(0);
            return guardado.toBuilder().id(99L).build();
        });

        CreateReservationResult resultado = useCase().createReservation(COMANDO);

        ArgumentCaptor<ReservationProcess> captor = ArgumentCaptor.forClass(ReservationProcess.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isEqualTo(ReservationProcess.builder().userId(7)
                .reservationProcessId("process-1").holdId("hold-1").professionalId(15).date(FECHA).startTime(HORA)
                .externalPatientId("user-7").status(ReservationStatus.HELD).expiresAt(VENCE)
                .createdDate(AHORA).lastModifiedDate(AHORA).build());
        assertThat(resultado).isInstanceOf(CreateReservationResult.Created.class);
        assertThat(((CreateReservationResult.Created) resultado).process().id()).isEqualTo(99L);
    }

    @Test
    void primero_valida_el_turno_despues_pide_el_hold_y_al_final_guarda() {
        turnoReservable();
        when(holdPort.createHold(15, FECHA, HORA, "user-7")).thenReturn(new HoldResult.Created(HOLD));
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        useCase().createReservation(COMANDO);

        InOrder orden = inOrder(slotChecker, holdPort, repository);
        orden.verify(slotChecker).checkSlot(15, FECHA, HORA);
        orden.verify(holdPort).createHold(15, FECHA, HORA, "user-7");
        orden.verify(repository).save(any());
    }

    @Test
    void si_el_turno_no_es_reservable_no_se_llama_a_la_catedra_ni_se_guarda() {
        when(slotChecker.checkSlot(15, FECHA, HORA)).thenReturn(new SlotCheckResult.InvalidSlot());

        assertThat(useCase().createReservation(COMANDO)).isInstanceOf(CreateReservationResult.InvalidSlot.class);

        verifyNoInteractions(holdPort, repository);
    }

    @Test
    void cada_motivo_de_la_validacion_se_traduce_sin_perderse() {
        when(slotChecker.checkSlot(15, FECHA, HORA)).thenReturn(
                new SlotCheckResult.ProfessionalDisabled(), new SlotCheckResult.ProfessionalNotFound(),
                new SlotCheckResult.CatalogUnavailable(), new SlotCheckResult.CatalogFailed());

        assertThat(useCase().createReservation(COMANDO)).isInstanceOf(CreateReservationResult.ProfessionalDisabled.class);
        assertThat(useCase().createReservation(COMANDO)).isInstanceOf(CreateReservationResult.ProfessionalNotFound.class);
        assertThat(useCase().createReservation(COMANDO)).isInstanceOf(CreateReservationResult.CatalogUnavailable.class);
        assertThat(useCase().createReservation(COMANDO)).isInstanceOf(CreateReservationResult.CatalogFailed.class);
        verifyNoInteractions(holdPort, repository);
    }

    @Test
    void cada_rechazo_de_la_catedra_se_traduce_y_no_se_guarda_nada() {
        turnoReservable();
        when(holdPort.createHold(15, FECHA, HORA, "user-7")).thenReturn(
                new HoldResult.SlotAlreadyHeld(), new HoldResult.SlotAlreadyReserved(), new HoldResult.InvalidSlot(),
                new HoldResult.ProfessionalDisabled(), new HoldResult.ProfessionalNotFound());

        assertThat(useCase().createReservation(COMANDO)).isInstanceOf(CreateReservationResult.SlotAlreadyHeld.class);
        assertThat(useCase().createReservation(COMANDO)).isInstanceOf(CreateReservationResult.SlotAlreadyReserved.class);
        assertThat(useCase().createReservation(COMANDO)).isInstanceOf(CreateReservationResult.InvalidSlot.class);
        assertThat(useCase().createReservation(COMANDO)).isInstanceOf(CreateReservationResult.ProfessionalDisabled.class);
        assertThat(useCase().createReservation(COMANDO)).isInstanceOf(CreateReservationResult.ProfessionalNotFound.class);
        verifyNoInteractions(repository);
    }

    @Test
    void una_falla_de_la_catedra_sale_como_excepcion_y_no_se_guarda_nada() {
        turnoReservable();
        when(holdPort.createHold(15, FECHA, HORA, "user-7")).thenThrow(new CatedraUnavailableException("sin conexión"));

        assertThatThrownBy(() -> useCase().createReservation(COMANDO)).isInstanceOf(CatedraUnavailableException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void si_guardar_falla_la_excepcion_sale_tal_cual() {
        turnoReservable();
        when(holdPort.createHold(15, FECHA, HORA, "user-7")).thenReturn(new HoldResult.Created(HOLD));
        IllegalStateException falla = new IllegalStateException("base caída");
        when(repository.save(any())).thenThrow(falla);

        assertThatThrownBy(() -> useCase().createReservation(COMANDO)).isSameAs(falla);
    }
}
