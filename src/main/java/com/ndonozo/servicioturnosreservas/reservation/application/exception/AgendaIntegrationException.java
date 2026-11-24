package com.ndonozo.servicioturnosreservas.reservation.application.exception;

public class AgendaIntegrationException extends RuntimeException {

    public AgendaIntegrationException(String message) {
        super(message);
    }

    public AgendaIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
