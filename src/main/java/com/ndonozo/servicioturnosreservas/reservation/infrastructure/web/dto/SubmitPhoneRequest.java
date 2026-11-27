package com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitPhoneRequest(
        @Schema(description = "Teléfono del paciente. Se aceptan espacios, guiones, paréntesis y puntos; sin ellos debe tener "
                + "entre 7 y 15 dígitos y puede empezar con +", example = "+54 9 261 555-1234")
        @NotBlank @Size(max = 40) String phoneNumber
) {
}
