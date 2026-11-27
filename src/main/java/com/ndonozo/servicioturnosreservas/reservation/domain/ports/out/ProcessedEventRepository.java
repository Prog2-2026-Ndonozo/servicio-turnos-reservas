package com.ndonozo.servicioturnosreservas.reservation.domain.ports.out;

import java.time.Instant;

public interface ProcessedEventRepository {
    /** Registra el evento. Devuelve false si ya estaba registrado. Corre en la transacción de quien la llama. */
    boolean markProcessed(String eventId, Instant processedAt);
}
