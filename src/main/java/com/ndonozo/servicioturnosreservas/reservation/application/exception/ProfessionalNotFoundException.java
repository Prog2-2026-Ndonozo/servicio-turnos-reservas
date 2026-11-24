package com.ndonozo.servicioturnosreservas.reservation.application.exception;

public class ProfessionalNotFoundException extends RuntimeException {

    public ProfessionalNotFoundException(long professionalId) {
        super("No existe el profesional " + professionalId);
    }
}
