package com.ndonozo.servicioturnosreservas.reservation.domain.model;

public record ConfirmReservationCommand(
        long userId,
        String reservationProcessId,
        String firstName,
        String lastName
) {
}
