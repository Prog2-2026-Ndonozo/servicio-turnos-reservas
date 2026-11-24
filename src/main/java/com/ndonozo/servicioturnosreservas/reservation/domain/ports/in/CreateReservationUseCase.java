package com.ndonozo.servicioturnosreservas.reservation.domain.ports.in;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.CreateReservationCommand;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.CreateReservationResult;

public interface CreateReservationUseCase {
    CreateReservationResult createReservation(CreateReservationCommand command);
}
