package com.ndonozo.servicioturnosreservas.availability.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

public record SlotResponse(
        @Schema(example = "2026-11-23")
        LocalDate date,
        @Schema(description = "Hora local de atención", type = "string", example = "09:00:00")
        LocalTime startTime,
        @Schema(description = "Hora local de atención", type = "string", example = "09:30:00")
        LocalTime endTime

) {
}
