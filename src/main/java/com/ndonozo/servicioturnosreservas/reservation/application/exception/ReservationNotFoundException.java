package com.ndonozo.servicioturnosreservas.reservation.application.exception;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(String message) {
        super(message);
    }
}
