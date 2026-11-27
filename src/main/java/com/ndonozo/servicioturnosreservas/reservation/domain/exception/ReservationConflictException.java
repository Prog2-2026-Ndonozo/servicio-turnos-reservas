package com.ndonozo.servicioturnosreservas.reservation.domain.exception;

/** Otro escritor guardó el proceso mientras este lo modificaba: hay que releer antes de decidir (ADR-0010). */
public class ReservationConflictException extends RuntimeException {

    public ReservationConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
