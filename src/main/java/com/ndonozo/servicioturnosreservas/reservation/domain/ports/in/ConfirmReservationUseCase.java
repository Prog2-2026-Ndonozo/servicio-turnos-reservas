package com.ndonozo.servicioturnosreservas.reservation.domain.ports.in;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.ConfirmReservationCommand;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ConfirmReservationResult;

public interface ConfirmReservationUseCase {
    ConfirmReservationResult confirmReservation(ConfirmReservationCommand command);
}
