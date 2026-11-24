package com.ndonozo.servicioturnosreservas.availability.application.exception;

public class InvalidDateRangeException extends RuntimeException {
    public InvalidDateRangeException(String message) {
        super(message);
    }
}
