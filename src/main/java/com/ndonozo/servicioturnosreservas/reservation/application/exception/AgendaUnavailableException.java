package com.ndonozo.servicioturnosreservas.reservation.application.exception;

public class AgendaUnavailableException extends RuntimeException {

    public AgendaUnavailableException(String message) {
        super(message);
    }

    public AgendaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
