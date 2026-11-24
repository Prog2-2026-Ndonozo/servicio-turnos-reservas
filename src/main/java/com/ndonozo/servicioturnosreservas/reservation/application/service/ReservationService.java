package com.ndonozo.servicioturnosreservas.reservation.application.service;

import com.ndonozo.servicioturnosreservas.reservation.application.exception.AgendaIntegrationException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.AgendaUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.InvalidSlotException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.ProfessionalDisabledException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.ProfessionalNotFoundException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.SlotAlreadyHeldException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.SlotAlreadyReservedException;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.CreateReservationCommand;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.CreateReservationResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.in.CreateReservationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final CreateReservationUseCase createReservation;

    public ReservationProcess createReservation(CreateReservationCommand command) {
        return switch (createReservation.createReservation(command)) {
            case CreateReservationResult.Created created -> created.process();
            case CreateReservationResult.InvalidSlot invalid ->
                    throw new InvalidSlotException("El turno no está disponible en la agenda del profesional");
            case CreateReservationResult.ProfessionalDisabled disabled ->
                    throw new ProfessionalDisabledException("El profesional está deshabilitado");
            case CreateReservationResult.ProfessionalNotFound notFound ->
                    throw new ProfessionalNotFoundException(command.professionalId());
            case CreateReservationResult.SlotAlreadyHeld held ->
                    throw new SlotAlreadyHeldException("El turno ya está retenido por otra reserva");
            case CreateReservationResult.SlotAlreadyReserved reserved ->
                    throw new SlotAlreadyReservedException("El turno ya está reservado");
            case CreateReservationResult.CatalogUnavailable unavailable ->
                    throw new AgendaUnavailableException("El catálogo no está disponible");
            case CreateReservationResult.CatalogFailed failed ->
                    throw new AgendaIntegrationException("Falló la integración con el catálogo");
        };
    }
}