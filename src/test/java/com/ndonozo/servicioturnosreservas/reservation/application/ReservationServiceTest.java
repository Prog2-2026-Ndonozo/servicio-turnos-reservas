package com.ndonozo.servicioturnosreservas.reservation.application;

import com.ndonozo.servicioturnosreservas.reservation.application.exception.AgendaIntegrationException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.AgendaUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.InvalidSlotException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.ProfessionalDisabledException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.ProfessionalNotFoundException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.SlotAlreadyHeldException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.SlotAlreadyReservedException;
import com.ndonozo.servicioturnosreservas.reservation.application.service.ReservationService;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.CreateReservationCommand;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.CreateReservationResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.in.CreateReservationUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** La fachada traduce cada resultado del caso de uso: el proceso creado, o la excepción que la capa web convierte en HTTP. */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final CreateReservationCommand COMANDO =
            new CreateReservationCommand(7, 15, LocalDate.of(2026, 11, 30), LocalTime.of(9, 30));

    @Mock
    private CreateReservationUseCase useCase;

    private ReservationService service() {
        return new ReservationService(useCase);
    }

    @Test
    void devuelve_el_proceso_creado() {
        ReservationProcess proceso = ReservationProcess.builder().reservationProcessId("process-1").build();
        when(useCase.createReservation(COMANDO)).thenReturn(new CreateReservationResult.Created(proceso));

        assertThat(service().createReservation(COMANDO)).isSameAs(proceso);
    }

    @Test
    void cada_resultado_que_no_es_exito_sale_como_su_excepcion() {
        when(useCase.createReservation(COMANDO)).thenReturn(
                new CreateReservationResult.InvalidSlot(), new CreateReservationResult.ProfessionalDisabled(),
                new CreateReservationResult.ProfessionalNotFound(), new CreateReservationResult.SlotAlreadyHeld(),
                new CreateReservationResult.SlotAlreadyReserved(), new CreateReservationResult.CatalogUnavailable(),
                new CreateReservationResult.CatalogFailed());

        assertThatThrownBy(() -> service().createReservation(COMANDO)).isInstanceOf(InvalidSlotException.class);
        assertThatThrownBy(() -> service().createReservation(COMANDO)).isInstanceOf(ProfessionalDisabledException.class);
        assertThatThrownBy(() -> service().createReservation(COMANDO)).isInstanceOf(ProfessionalNotFoundException.class)
                .hasMessageContaining("15");
        assertThatThrownBy(() -> service().createReservation(COMANDO)).isInstanceOf(SlotAlreadyHeldException.class);
        assertThatThrownBy(() -> service().createReservation(COMANDO)).isInstanceOf(SlotAlreadyReservedException.class);
        assertThatThrownBy(() -> service().createReservation(COMANDO)).isInstanceOf(AgendaUnavailableException.class);
        assertThatThrownBy(() -> service().createReservation(COMANDO)).isInstanceOf(AgendaIntegrationException.class);
    }
}
