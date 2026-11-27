package com.ndonozo.servicioturnosreservas.reservation.domain.model;

import java.time.Instant;

/** El resultado del proceso que publica la cátedra (I §15.6 a §15.9), reducido a lo que turnos necesita. */
public sealed interface AppointmentResultEvent {

    String eventId();

    String reservationProcessId();

    String holdId();

    record Confirmed(String eventId, String reservationProcessId, String holdId, long reservationId)
            implements AppointmentResultEvent {}

    /** {@code expiresAt} puede venir vacío: entonces el proceso conserva el que tenía. */
    record Rejected(String eventId, String reservationProcessId, String holdId, String reason, Instant expiresAt)
            implements AppointmentResultEvent {}

    record Expired(String eventId, String reservationProcessId, String holdId) implements AppointmentResultEvent {}

    record Invalid(String eventId, String reservationProcessId, String holdId) implements AppointmentResultEvent {}
}
