package com.ndonozo.servicioturnosreservas.availability.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record AvailabilityRequest(
        @Schema(description = "Profesional del que se piden los turnos libres", example = "15")
        @NotNull @Positive Long professionalId,
        @Schema(description = "Primer día del rango, inclusive", example = "2026-11-23")
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @Schema(description = "Último día del rango, inclusive. No puede ser anterior a from y el rango no puede superar los 31 días",
                example = "2026-11-29")
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
) {}