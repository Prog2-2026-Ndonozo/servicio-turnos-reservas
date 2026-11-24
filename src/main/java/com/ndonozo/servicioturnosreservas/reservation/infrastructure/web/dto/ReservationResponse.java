package com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ReservationResponse(
        @Schema(description = "Identificador del proceso de reserva", example = "process-a92b47d0")
        String reservationProcessId,
        @Schema(description = "Estado local del proceso", example = "HELD")
        String status,
        @Schema(description = "Momento en que vence el turno retenido (UTC). Pasado ese momento hay que empezar de nuevo",
                example = "2026-11-30T14:44:50Z")
        Instant expiresAt
) {
}
