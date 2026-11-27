package com.ndonozo.servicioturnosreservas.availability.application.usecases;

import com.ndonozo.servicioturnosreservas.availability.domain.model.SlotCalculator;
import com.ndonozo.servicioturnosreservas.availability.domain.model.SlotCheckResult;
import com.ndonozo.servicioturnosreservas.availability.domain.ports.in.CheckSlotReservableUseCase;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalAgenda;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalAgendaResult;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.in.GetProfessionalAgendaUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Responde "¿este turno se puede reservar?" con datos vigentes del catálogo. Lo usa reservation antes de pedir el
 * hold a la cátedra (E §4.2, E §8). No pasa por la fachada: se consume directo por su puerto de entrada.
 * No consulta ocupaciones: que el turno esté ocupado lo decide la cátedra al crear el hold.
 */
@Component
@RequiredArgsConstructor
public class CheckSlotReservableUseCaseImpl implements CheckSlotReservableUseCase {

    // Puerto de entrada de OTRO módulo (professional).
    private final GetProfessionalAgendaUseCase agendas;
    private final Clock clock;

    @Override
    public SlotCheckResult checkSlot(long professionalId, LocalDate date, LocalTime startTime) {
        return switch (agendas.getAgenda(professionalId)) {
            case ProfessionalAgendaResult.Found found -> check(found.agenda(), date, startTime);
            case ProfessionalAgendaResult.NotFound notFound -> new SlotCheckResult.ProfessionalNotFound();
            case ProfessionalAgendaResult.CatalogUnavailable unavailable -> new SlotCheckResult.CatalogUnavailable();
            case ProfessionalAgendaResult.CatalogFailed failed -> new SlotCheckResult.CatalogFailed();
        };
    }

    private SlotCheckResult check(ProfessionalAgenda agenda, LocalDate date, LocalTime startTime) {
        if (!agenda.getProfessional().isEnabled()) {
            return new SlotCheckResult.ProfessionalDisabled();
        }
        // Mismo cálculo que la disponibilidad, sin ocupaciones: el turno tiene que existir en el horario y no haber empezado.
        boolean reservable = SlotCalculator
                .freeSlots(agenda.getWeeklySchedules(), List.of(), date, date, LocalDateTime.now(clock))
                .stream()
                .anyMatch(slot -> slot.startTime().equals(startTime));
        return reservable ? new SlotCheckResult.Reservable() : new SlotCheckResult.InvalidSlot();
    }
}