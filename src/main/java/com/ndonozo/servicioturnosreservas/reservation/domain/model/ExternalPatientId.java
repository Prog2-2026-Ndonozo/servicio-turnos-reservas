package com.ndonozo.servicioturnosreservas.reservation.domain.model;

/** Identificador estable del usuario ante la cátedra (I §2): su id interno, nunca el login. */
public final class ExternalPatientId {

    private static final String PREFIX = "user-";

    private ExternalPatientId() {
    }

    public static String of(long userId) {
        return PREFIX + userId;
    }
}