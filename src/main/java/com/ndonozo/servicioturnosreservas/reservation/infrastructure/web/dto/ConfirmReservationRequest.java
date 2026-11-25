package com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmReservationRequest(
        @Schema(description = "Nombre del paciente (2 a 100 caracteres)", example = "Juan")
        @NotBlank @Size(min = 2, max = 100) String firstName,
        @Schema(description = "Apellido del paciente (2 a 100 caracteres)", example = "Perez")
        @NotBlank @Size(min = 2, max = 100) String lastName
) {
}
