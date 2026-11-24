package com.ndonozo.servicioturnosreservas.reservation.application.exception;

public class CatedraIntegrationException extends RuntimeException {

    public CatedraIntegrationException(String message) {
        super(message);
    }

    public CatedraIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
