package com.ndonozo.servicioturnosreservas.reservation.application.exception;

public class HoldExpiredException extends RuntimeException {

    public HoldExpiredException(String message) {
        super(message);
    }
}
