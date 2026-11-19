package com.ndonozo.servicioturnosreservas.user.infrastructure.web.dto;


import com.ndonozo.servicioturnosreservas.user.infrastructure.web.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = RegisterRequest.LOGIN_REGEX) String login,
        @NotBlank @Size(min = 4) @MaxUtf8Bytes(72) String password,
        @Size(max = 50) String firstName,
        @Size(max = 50) String lastName,
        @NotBlank @Email @Size(min = 5, max = 254) String email,
        @Size(max = 256) String imageUrl,
        @NotBlank @Size(min = 2, max = 10) String langKey
) {
    // Patrón de login de JHipster (ADR-0003; supuesto a confirmar con la cátedra)
    public static final String LOGIN_REGEX =
            "^[a-zA-Z0-9!$&*_=^`{|}~.-]+@?[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*$|^[_.@A-Za-z0-9-]+$";

    // El toString por defecto de un record imprimiría la contraseña si alguien lo loguea.
    @Override
    public String toString() {
        return "RegisterRequest[login=" + login + "]";
    }
}