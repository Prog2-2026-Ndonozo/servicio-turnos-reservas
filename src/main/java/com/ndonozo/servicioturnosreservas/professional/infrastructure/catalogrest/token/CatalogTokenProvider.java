package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.token;

import java.time.Clock;
import java.time.Duration;

public class CatalogTokenProvider {

    static final long MARGEN_SEGUNDOS = 30;
    private static final Duration MARGEN = Duration.ofSeconds(MARGEN_SEGUNDOS);

    private final CatalogTokenIssuer issuer;
    private final Clock clock;
    private IssuedToken current;

    public CatalogTokenProvider(CatalogTokenIssuer issuer, Clock clock) {
        this.issuer = issuer;
        this.clock = clock;
    }

    public synchronized String token() {
        if (current == null || !clock.instant().isBefore(current.expiresAt().minus(MARGEN))) {
            current = issuer.issue();
        }
        return current.value();
    }

    /** Solo se descarta si sigue siendo el vigente, para no tirar uno recién emitido por otro hilo. */
    public synchronized void discard(String rejectedToken) {
        if (current != null && current.value().equals(rejectedToken)) {
            current = null;
        }
    }
}