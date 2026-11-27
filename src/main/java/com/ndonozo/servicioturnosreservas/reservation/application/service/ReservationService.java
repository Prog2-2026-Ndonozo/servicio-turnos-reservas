package com.ndonozo.servicioturnosreservas.reservation.application.service;

import com.ndonozo.servicioturnosreservas.reservation.application.exception.AgendaIntegrationException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.AgendaUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.InvalidSlotException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.ProfessionalDisabledException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.ProfessionalNotFoundException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.SlotAlreadyHeldException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.SlotAlreadyReservedException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.HoldExpiredException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.InvalidPhoneNumberException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.InvalidReservationStateException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.ReservationNotFoundException;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ConfirmReservationCommand;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ConfirmReservationResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.CreateReservationCommand;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.CreateReservationResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneRequestResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneRequestedEvent;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.SubmitPhoneCommand;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.SubmitPhoneResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.in.ConfirmReservationUseCase;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.in.CreateReservationUseCase;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.in.HandlePhoneRequestedUseCase;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.in.SubmitPhoneUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final CreateReservationUseCase createReservation;
    private final ConfirmReservationUseCase confirmReservation;
    private final HandlePhoneRequestedUseCase handlePhoneRequested;
    private final SubmitPhoneUseCase submitPhone;

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

    public ReservationProcess confirmReservation(ConfirmReservationCommand command) {
        return switch (confirmReservation.confirmReservation(command)) {
            case ConfirmReservationResult.Confirmed confirmed -> confirmed.process();
            case ConfirmReservationResult.NotFound notFound ->
                    throw new ReservationNotFoundException("No existe la reserva " + command.reservationProcessId());
            case ConfirmReservationResult.InvalidState invalidState ->
                    throw new InvalidReservationStateException("La reserva no se puede confirmar en su estado actual");
            case ConfirmReservationResult.HoldExpired expired ->
                    throw new HoldExpiredException("El turno retenido venció: hay que empezar de nuevo");
            case ConfirmReservationResult.SlotAlreadyReserved reserved ->
                    throw new SlotAlreadyReservedException("El turno ya está reservado");
        };
    }

    public ReservationProcess submitPhone(SubmitPhoneCommand command) {
        return switch (submitPhone.submitPhone(command)) {
            case SubmitPhoneResult.Submitted submitted -> submitted.process();
            case SubmitPhoneResult.NotFound notFound ->
                    throw new ReservationNotFoundException("No existe la reserva " + command.reservationProcessId());
            case SubmitPhoneResult.InvalidState invalidState ->
                    throw new InvalidReservationStateException("La reserva no está esperando un teléfono");
            case SubmitPhoneResult.PhoneNotRequested notRequested ->
                    throw new InvalidReservationStateException(
                            "La cátedra todavía no pidió el teléfono: intentá de nuevo en unos segundos");
            case SubmitPhoneResult.HoldExpired expired ->
                    throw new HoldExpiredException("El turno retenido venció: hay que empezar de nuevo");
            case SubmitPhoneResult.InvalidPhone invalidPhone ->
                    throw new InvalidPhoneNumberException("El teléfono no es válido: debe tener entre 7 y 15 dígitos y puede empezar con +");
        };
    }

    /** Lo llama el listener de Kafka, que no es HTTP: devuelve el resultado para que lo registre en el log. */
    public PhoneRequestResult handlePhoneRequested(PhoneRequestedEvent event) {
        return handlePhoneRequested.handlePhoneRequested(event);
    }
}
