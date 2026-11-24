package com.ndonozo.servicioturnosreservas.availability.application.exception;

public class CatedraIntegrationException extends RuntimeException {
    public CatedraIntegrationException(String message) {
        super(message);
    }

    public CatedraIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
