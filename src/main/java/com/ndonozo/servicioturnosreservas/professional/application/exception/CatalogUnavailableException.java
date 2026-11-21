package com.ndonozo.servicioturnosreservas.professional.application.exception;

public class CatalogUnabailavbleException extends RuntimeException {
    public CatalogUnavailableException(String message) { super(message); }
    public CatalogUnavailableException(String message, Throwable cause) { super(message, cause);
}
