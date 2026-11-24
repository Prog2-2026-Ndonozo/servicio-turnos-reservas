package com.ndonozo.servicioturnosreservas.availability.application.exception;

public class ProfessionalNotFoundException extends RuntimeException {
    public ProfessionalNotFoundException(long professionalId) {
        super("No existe el profesional " + professionalId);
    }
}
