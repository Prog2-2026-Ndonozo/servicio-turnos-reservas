package com.ndonozo.servicioturnosreservas.reservation.domain.model;

import java.util.Optional;
import java.util.regex.Pattern;

/** Teléfono ya normalizado como lo hace la cátedra (I §15.5): sin espacios, guiones, paréntesis ni puntos. */
public record PhoneNumber(String value) {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-().]");
    private static final Pattern VALID = Pattern.compile("^\\+?[0-9]{7,15}$");

    /** Vacío si, una vez quitados los separadores, no cumple {@code ^\+?[0-9]{7,15}$}. */
    public static Optional<PhoneNumber> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = SEPARATORS.matcher(raw).replaceAll("");
        return VALID.matcher(normalized).matches() ? Optional.of(new PhoneNumber(normalized)) : Optional.empty();
    }
}
