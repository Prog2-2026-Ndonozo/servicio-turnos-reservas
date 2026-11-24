package com.ndonozo.servicioturnosreservas.reservation.application.exception;

public class CatedraUnavailableException extends RuntimeException {

    public CatedraUnavailableException(String message) {
        super(message);
    }

    public CatedraUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
