package com.ndonozo.servicioturnosreservas.user.infrastructure.web.dto;

import com.ndonozo.servicioturnosreservas.user.infrastructure.web.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// "username" como en el LoginVM de JHipster. Un "rememberMe" que mande el cliente se ignora.
public record AuthenticateRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @MaxUtf8Bytes(72) String password
) {
    @Override
    public String toString() {
        return "AuthenticateRequest[username=" + username + "]";
    }
}