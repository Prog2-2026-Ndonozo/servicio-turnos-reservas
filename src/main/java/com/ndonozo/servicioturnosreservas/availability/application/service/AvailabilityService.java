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

@Service
@RequiredArgsConstructor
public class AvailabilityService {
    private final GetAvailabilityUseCase getAvailability;

    public Availability getAvailability(long professionalId, LocalDate from, LocalDate to){
        return switch (getAvailability.getAvailability(professionalId,from,to)){
            case AvailabilityResult.Found found -> found.availability();
            case AvailabilityResult.InvalidRange invalid -> throw new InvalidDateRangeException(invalid.message());
            case AvailabilityResult.ProfessionalNotFound notFound-> throw new ProfessionalNotFoundException(professionalId);
            case AvailabilityResult.CatalogUnavailable unavailable-> throw new AgendaUnavailableException("El catalogo no esta disponible");
            case AvailabilityResult.CatalogFailed failed -> throw new AgendaIntegrationException("Fallo la integracion del catalogo");
        };
    }
}
