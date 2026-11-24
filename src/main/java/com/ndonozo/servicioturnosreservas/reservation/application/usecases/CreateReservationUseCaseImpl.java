package com.ndonozo.servicioturnosreservas.reservation.application.usecases;

import com.ndonozo.servicioturnosreservas.availability.domain.model.SlotCheckResult;
import com.ndonozo.servicioturnosreservas.availability.domain.ports.in.CheckSlotReservableUseCase;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.*;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.in.CreateReservationUseCase;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.HoldPort;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateReservationUseCaseImpl implements CreateReservationUseCase {

    private final CheckSlotReservableUseCase slotChecker;
    private final HoldPort holdPort;
    private final ReservationProcessRepository repository;
    private final Clock clock;

    @Override
    public CreateReservationResult createReservation(CreateReservationCommand command) {
        // Antes de pedir nada a la cátedra se valida el turno con datos vigentes del catálogo (E §4.2, E §8).
        return switch (slotChecker.checkSlot(command.professionalId(), command.date(), command.startTime())) {
            case SlotCheckResult.Reservable reservable -> requestHold(command);
            case SlotCheckResult.InvalidSlot invalid -> new CreateReservationResult.InvalidSlot();
            case SlotCheckResult.ProfessionalDisabled disabled -> new CreateReservationResult.ProfessionalDisabled();
            case SlotCheckResult.ProfessionalNotFound notFound -> new CreateReservationResult.ProfessionalNotFound();
            case SlotCheckResult.CatalogUnavailable unavailable -> new CreateReservationResult.CatalogUnavailable();
            case SlotCheckResult.CatalogFailed failed -> new CreateReservationResult.CatalogFailed();
        };
    }

    private CreateReservationResult requestHold(CreateReservationCommand command) {
        HoldResult result = holdPort.createHold(command.professionalId(), command.date(), command.startTime(),
                ExternalPatientId.of(command.userId()));
        return switch (result) {
            case HoldResult.Created created -> new CreateReservationResult.Created(save(command, created.hold()));
            case HoldResult.SlotAlreadyHeld held -> new CreateReservationResult.SlotAlreadyHeld();
            case HoldResult.SlotAlreadyReserved reserved -> new CreateReservationResult.SlotAlreadyReserved();
            case HoldResult.InvalidSlot invalid -> new CreateReservationResult.InvalidSlot();
            case HoldResult.ProfessionalDisabled disabled -> new CreateReservationResult.ProfessionalDisabled();
            case HoldResult.ProfessionalNotFound notFound -> new CreateReservationResult.ProfessionalNotFound();
        };
    }

    private ReservationProcess save(CreateReservationCommand command, Hold hold) {
        Instant now = Instant.now(clock);
        ReservationProcess process = ReservationProcess.builder()
                .userId(command.userId())
                .reservationProcessId(hold.reservationProcessId())
                .holdId(hold.holdId())
                .professionalId(command.professionalId())
                .date(command.date())
                .startTime(command.startTime())
                .externalPatientId(ExternalPatientId.of(command.userId()))
                .status(ReservationStatus.HELD)
                .expiresAt(hold.expiresAt())
                .createdDate(now)
                .lastModifiedDate(now)
                .build();
        try {
            return repository.save(process);
        } catch (RuntimeException e) {
            // El hold ya existe en la cátedra y no se puede deshacer: vence solo en expiresAt (ADR-0008).
            log.error("No se pudo guardar el proceso {}: el hold {} quedó creado en la cátedra hasta {}",
                    hold.reservationProcessId(), hold.holdId(), hold.expiresAt(), e);
            throw e;
        }
    }
}