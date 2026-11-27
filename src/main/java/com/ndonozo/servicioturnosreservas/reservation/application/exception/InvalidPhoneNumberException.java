package com.ndonozo.servicioturnosreservas.reservation.application.exception;

public class InvalidPhoneNumberException extends RuntimeException {

    public InvalidPhoneNumberException(String message) {
        super(message);
    }
}
