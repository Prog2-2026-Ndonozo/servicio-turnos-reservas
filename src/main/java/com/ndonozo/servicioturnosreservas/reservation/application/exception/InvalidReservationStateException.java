package com.ndonozo.servicioturnosreservas.reservation.application.exception;

public class InvalidReservationStateException extends RuntimeException {

    public InvalidReservationStateException(String message) {
        super(message);
    }
}
