package com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateReservationRequest(
        @Schema(description = "Profesional del turno", example = "15")
        @NotNull @Positive Long professionalId,
        @Schema(description = "Fecha del turno (yyyy-MM-dd)", example = "2026-11-30")
        @NotNull LocalDate date,
        @Schema(description = "Hora local de inicio del turno (HH:mm), tal como la devuelve la disponibilidad",
                type = "string", example = "09:30")
        @NotNull LocalTime startTime
) {
}
