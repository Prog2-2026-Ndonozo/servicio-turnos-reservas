package com.ndonozo.servicioturnosreservas.availability.application.usecases;

import com.ndonozo.servicioturnosreservas.availability.domain.model.Availability;
import com.ndonozo.servicioturnosreservas.availability.domain.model.AvailabilityResult;
import com.ndonozo.servicioturnosreservas.availability.domain.model.Occupancy;
import com.ndonozo.servicioturnosreservas.availability.domain.model.SlotCalculator;
import com.ndonozo.servicioturnosreservas.availability.domain.ports.in.GetAvailabilityUseCase;
import com.ndonozo.servicioturnosreservas.availability.domain.ports.out.OccupancyPort;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalAgenda;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalAgendaResult;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.in.GetProfessionalAgendaUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GetAvailabilityUseCaseImpl implements GetAvailabilityUseCase {
    static final int MAX_RANGE_DAYS = 31;

    private final GetProfessionalAgendaUseCase agendas;
    private final OccupancyPort occupancyPort;
    private final Clock clock;

    @Override
    public AvailabilityResult getAvailability(long professionalId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            return new AvailabilityResult.InvalidRange("La fecha inicial no puede ser posterior a la final");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            return new AvailabilityResult.InvalidRange("El rango no puede superar los " + MAX_RANGE_DAYS + " días");
        }
        return switch (agendas.getAgenda(professionalId)) {
            case ProfessionalAgendaResult.Found found -> available(found.agenda(), professionalId, from, to);
            case ProfessionalAgendaResult.NotFound notFound -> new AvailabilityResult.ProfessionalNotFound();
            case ProfessionalAgendaResult.CatalogUnavailable unavailable -> new AvailabilityResult.CatalogUnavailable();
            case ProfessionalAgendaResult.CatalogFailed failed -> new AvailabilityResult.CatalogFailed();
        };
    }

    private AvailabilityResult available(ProfessionalAgenda agenda, long professionalId, LocalDate from, LocalDate to) {
        // Un profesional deshabilitado no ofrece turnos: no hace falta preguntarle nada a la cátedra.
        if (!agenda.getProfessional().isEnabled()) {
            return new AvailabilityResult.Found(new Availability(professionalId, from, to, List.of()));
        }
        // Las ocupaciones se consultan en cada pedido: lo que dice la cátedra ahora es lo que vale (E §3, E §4.2).
        List<Occupancy> occupancies = occupancyPort.findOccupancies(professionalId, from, to);
        return new AvailabilityResult.Found(new Availability(professionalId, from, to,
                SlotCalculator.freeSlots(agenda.getWeeklySchedules(), occupancies, from, to, LocalDateTime.now(clock))));
    }
}
