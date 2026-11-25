package com.ndonozo.servicioturnosreservas.reservation.application.usecases;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.ConfirmHoldResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ConfirmReservationCommand;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ConfirmReservationResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationStatus;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.in.ConfirmReservationUseCase;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.HoldPort;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ConfirmReservationUseCaseImpl implements ConfirmReservationUseCase {

    private final ReservationProcessRepository repository;
    private final HoldPort holdPort;
    private final Clock clock;

    @Override
    public ConfirmReservationResult confirmReservation(ConfirmReservationCommand command) {
        // Un proceso ajeno se trata igual que uno inexistente: no se revela que existe (E §9).
        return repository.findByReservationProcessIdAndUserId(command.reservationProcessId(), command.userId())
                .map(process -> confirm(process, command))
                .orElseGet(ConfirmReservationResult.NotFound::new);
    }

    private ConfirmReservationResult confirm(ReservationProcess process, ConfirmReservationCommand command) {
        // Idempotente: el evento de Kafka puede haber llegado antes y haber movido ya el proceso.
        if (process.status() == ReservationStatus.WAITING_FOR_PHONE) {
            return new ConfirmReservationResult.Confirmed(process);
        }
        if (process.status() != ReservationStatus.HELD) {
            return new ConfirmReservationResult.InvalidState();
        }
        Instant now = Instant.now(clock);
        if (!now.isBefore(process.expiresAt())) {
            // Un hold vencido no se confirma: se cierra sin llamar a la cátedra (I §18.3).
            repository.save(process.transitionTo(ReservationStatus.EXPIRED, now));
            return new ConfirmReservationResult.HoldExpired();
        }
        // Un solo intento y fuera de una transacción de base: un timeout no prueba que falló (I §18.4).
        return switch (holdPort.confirmHold(process.holdId(), process.externalPatientId(),
                command.firstName(), command.lastName())) {
            case ConfirmHoldResult.Confirmed confirmed -> waitingForPhone(process, now);
            case ConfirmHoldResult.AlreadyConfirmed alreadyConfirmed -> waitingForPhone(process, now);
            case ConfirmHoldResult.HoldExpired expired -> {
                repository.save(process.transitionTo(ReservationStatus.EXPIRED, now));
                yield new ConfirmReservationResult.HoldExpired();
            }
            case ConfirmHoldResult.SlotAlreadyReserved reserved -> new ConfirmReservationResult.SlotAlreadyReserved();
        };
    }

    private ConfirmReservationResult waitingForPhone(ReservationProcess process, Instant now) {
        return new ConfirmReservationResult.Confirmed(
                repository.save(process.transitionTo(ReservationStatus.WAITING_FOR_PHONE, now)));
    }
}
