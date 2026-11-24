package com.ndonozo.servicioturnosreservas.reservation.application.exception;

public class SlotAlreadyReservedException extends RuntimeException {

    public SlotAlreadyReservedException(String message) {
        super(message);
    }
}
