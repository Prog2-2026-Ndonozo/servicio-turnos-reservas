package com.ndonozo.servicioturnosreservas.reservation.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateReservationCommand(
        long userId,
        long professionalId,
        LocalDate date,
        LocalTime startTime
) {
}
