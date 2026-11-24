package com.ndonozo.servicioturnosreservas.availability.application.exception;

public class CatedraUnavailableException extends RuntimeException {
    public CatedraUnavailableException(String message) {
        super(message);
    }

    public CatedraUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
