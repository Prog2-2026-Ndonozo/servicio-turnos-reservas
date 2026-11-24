package com.ndonozo.servicioturnosreservas.reservation.application.exception;

public class InvalidSlotException extends RuntimeException {

    public InvalidSlotException(String message) {
        super(message);
    }
}
