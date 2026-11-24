package com.ndonozo.servicioturnosreservas.reservation.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Builder(toBuilder=true)
public record ReservationProcess(
        Long id,
        long userId,
        String reservationProcessId,
        String holdId,
        long professionalId,
        LocalDate date,
        LocalTime startTime,
        String externalPatientId,
        ReservationStatus status,
        Instant expiresAt,
        Instant createdDate,
        Instant lastModifiedDate
) {
    /** Devuelve el proceso en el estado nuevo, o falla si la máquina de estados no lo permite. */
    public ReservationProcess transitionTo(ReservationStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Transición no permitida: " + status + " -> " + target);
        }
        return toBuilder().status(target).lastModifiedDate(now).build();
    }
}
