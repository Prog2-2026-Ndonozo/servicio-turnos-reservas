package com.ndonozo.servicioturnosreservas.reservation.infrastructure.persistence.adapter;

import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class JdbcProcessedEventRepositoryAdapter implements ProcessedEventRepository {

    private final JdbcTemplate jdbc;

    // JdbcTemplate usa la misma conexión que JPA dentro de una transacción: el insert queda junto con el efecto.
    @Override
    public boolean markProcessed(String eventId, Instant processedAt) {
        return jdbc.update("INSERT INTO processed_event (event_id, processed_at) VALUES (?, ?) ON CONFLICT (event_id) DO NOTHING",
                eventId, Timestamp.from(processedAt)) == 1;
    }
}
