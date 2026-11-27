package com.ndonozo.servicioturnosreservas.reservation.application.usecases;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.AppointmentResultEvent;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.AppointmentResultOutcome;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationStatus;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.in.HandleAppointmentResultUseCase;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.ProcessedEventRepository;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class HandleAppointmentResultUseCaseImpl implements HandleAppointmentResultUseCase {

    private final ProcessedEventRepository processedEvents;
    private final ReservationProcessRepository repository;
    private final Clock clock;

    /** Una sola transacción, como el pedido de teléfono: si guardar falla, el eventId tampoco queda registrado (ADR-0010). */
    @Override
    @Transactional
    public AppointmentResultOutcome handleResult(AppointmentResultEvent event) {
        Instant now = Instant.now(clock);
        if (!processedEvents.markProcessed(event.eventId(), now)) {
            return new AppointmentResultOutcome.Duplicate();
        }
        return repository.findByReservationProcessId(event.reservationProcessId())
                .map(process -> apply(process, event, now))
                .orElseGet(AppointmentResultOutcome.ProcessNotFound::new);
    }

    private AppointmentResultOutcome apply(ReservationProcess process, AppointmentResultEvent event, Instant now) {
        if (!process.holdId().equals(event.holdId())) {
            return new AppointmentResultOutcome.HoldMismatch();
        }
        // La máquina de estados decide qué se puede mover: un estado final o CONFIRMED no se altera (E §7, I §18.3).
        return switch (event) {
            case AppointmentResultEvent.Confirmed confirmed -> process.status() == ReservationStatus.PHONE_SUBMITTED
                    ? applied(process.confirmedAs(confirmed.reservationId(), now))
                    : new AppointmentResultOutcome.NotApplicable();
            case AppointmentResultEvent.Rejected rejected -> process.status() == ReservationStatus.PHONE_SUBMITTED
                    ? applied(process.rejectedWith(rejected.reason(), rejected.expiresAt(), now))
                    : new AppointmentResultOutcome.NotApplicable();
            case AppointmentResultEvent.Expired expired -> process.status().canTransitionTo(ReservationStatus.EXPIRED)
                    ? applied(process.transitionTo(ReservationStatus.EXPIRED, now))
                    : new AppointmentResultOutcome.NotApplicable();
            case AppointmentResultEvent.Invalid invalid -> process.status().canTransitionTo(ReservationStatus.FAILED)
                    ? applied(process.transitionTo(ReservationStatus.FAILED, now))
                    : new AppointmentResultOutcome.NotApplicable();
        };
    }

    private AppointmentResultOutcome applied(ReservationProcess process) {
        return new AppointmentResultOutcome.Applied(repository.save(process));
    }
}
