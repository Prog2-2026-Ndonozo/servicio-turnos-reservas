package com.ndonozo.servicioturnosreservas.availability.application.service;

import com.ndonozo.servicioturnosreservas.availability.application.exception.AgendaIntegrationException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.AgendaUnavailableException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.InvalidDateRangeException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.ProfessionalNotFoundException;
import com.ndonozo.servicioturnosreservas.availability.domain.model.Availability;
import com.ndonozo.servicioturnosreservas.availability.domain.model.AvailabilityResult;
import com.ndonozo.servicioturnosreservas.availability.domain.ports.in.GetAvailabilityUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Fachada del módulo availability: la llama el controller de disponibilidad. Traduce cada resultado del caso de
 * uso a una excepción de aplicación (el @RestControllerAdvice las convierte en problem+json).
 * El otro caso de uso del módulo (CheckSlotReservableUseCase) no pasa por acá: lo usa reservation directamente.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {
    private final GetAvailabilityUseCase getAvailability;

    public Availability getAvailability(long professionalId, LocalDate from, LocalDate to){
        // Found devuelve los turnos libres; rango inválido es 400; profesional inexistente 404; catálogo caído 503;
        // catálogo con error 502.
        return switch (getAvailability.getAvailability(professionalId,from,to)){
            case AvailabilityResult.Found found -> found.availability();
            case AvailabilityResult.InvalidRange invalid -> throw new InvalidDateRangeException(invalid.message());
            case AvailabilityResult.ProfessionalNotFound notFound-> throw new ProfessionalNotFoundException(professionalId);
            case AvailabilityResult.CatalogUnavailable unavailable-> throw new AgendaUnavailableException("El catalogo no esta disponible");
            case AvailabilityResult.CatalogFailed failed -> throw new AgendaIntegrationException("Fallo la integracion del catalogo");
        };
    }
}
