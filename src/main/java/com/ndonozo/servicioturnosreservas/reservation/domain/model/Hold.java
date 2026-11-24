package com.ndonozo.servicioturnosreservas.reservation.domain.model;

import java.time.Instant;

public record Hold(
        String holdId,
        String reservationProcessId,
        Instant expiresAt
) {
}
