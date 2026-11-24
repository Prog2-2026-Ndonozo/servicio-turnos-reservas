package com.ndonozo.servicioturnosreservas.reservation.application.exception;

public class SlotAlreadyHeldException extends RuntimeException {

    public SlotAlreadyHeldException(String message) {
        super(message);
    }
}
