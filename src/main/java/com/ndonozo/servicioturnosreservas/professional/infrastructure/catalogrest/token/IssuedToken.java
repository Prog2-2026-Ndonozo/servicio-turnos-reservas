package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.token;

import java.time.Instant;

public record IssuedToken(String value, Instant expiresAt) {

    // Un token es un secreto: si alguien imprime este objeto no debe salir su valor.
    @Override
    public String toString() {
        return "IssuedToken[expiresAt=" + expiresAt + "]";
    }
}