package com.ndonozo.servicioturnosreservas.professional.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record ProfessionalSearchRequest(

        @Schema(description = "Categoría del profesional", example = "101")
        @Positive Long categoryId,

        @Schema(description = "Texto dentro del nombre o del apellido, o de ambos juntos. No distingue mayúsculas pero sí tildes. En blanco equivale a no filtrar",
                example = "ana", maxLength = 100)
        @Size(max = 100) String name,

        @Schema(description = "true: solo habilitados; false: solo deshabilitados; sin valor: todos")
        Boolean enabled,

        @Schema(description = "Prefiltro: profesionales que atienden ese día de la semana (no son turnos libres)",
                example = "2026-11-23")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate availableOn
) {
}