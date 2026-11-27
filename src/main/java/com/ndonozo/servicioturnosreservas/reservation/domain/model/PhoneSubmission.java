package com.ndonozo.servicioturnosreservas.reservation.domain.model;

import java.time.Instant;

/** El teléfono que turnos le envía a la cátedra (I §15.5), reducido a lo que decide el caso de uso. */
public record PhoneSubmission(
        String eventId,
        String reservationProcessId,
        String holdId,
        String requestEventId,
        String phoneNumber,
        Instant occurredAt
) {
}
