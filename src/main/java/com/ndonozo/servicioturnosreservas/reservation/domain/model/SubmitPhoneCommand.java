package com.ndonozo.servicioturnosreservas.reservation.domain.model;

public record SubmitPhoneCommand(
        long userId,
        String reservationProcessId,
        String phoneNumber
) {
}
