package com.ndonozo.servicioturnosreservas.availability.application.exception;

public class AgendaIntegrationException extends RuntimeException {
    public AgendaIntegrationException(String message) {
        super(message);
    }

    public AgendaIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
