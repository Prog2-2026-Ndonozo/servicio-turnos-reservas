package com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.client;

import lombok.Getter;

/** La cátedra rechazó el hold por una regla de negocio conocida (I §13.1). Es una respuesta, no una falla. */
@Getter
public class CatedraHoldRejectedException extends RuntimeException {
    private final String code;

    public CatedraHoldRejectedException(String code) {
        super("La cátedra rechazó el hold: " + code);
        this.code = code;
    }
}