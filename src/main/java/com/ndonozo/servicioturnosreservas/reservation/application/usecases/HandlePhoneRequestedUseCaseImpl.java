package com.ndonozo.servicioturnosreservas.reservation.application.usecases;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneRequestResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneRequestedEvent;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationStatus;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.in.HandlePhoneRequestedUseCase;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.ProcessedEventRepository;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class HandlePhoneRequestedUseCaseImpl implements HandlePhoneRequestedUseCase {

    private final ProcessedEventRepository processedEvents;
    private final ReservationProcessRepository repository;
    private final Clock clock;

    /**
     * Todo en una transacción: si guardar falla (por ejemplo, un choque de versión), el eventId tampoco queda registrado
     * y el reintento de Spring Kafka vuelve a intentar con el estado anterior intacto (ADR-0010).
     */
    @Override
    @Transactional
    public PhoneRequestResult handlePhoneRequested(PhoneRequestedEvent event) {
        Instant now = Instant.now(clock);
        if (!processedEvents.markProcessed(event.eventId(), now)) {
            return new PhoneRequestResult.Duplicate();
        }
        return repository.findByReservationProcessId(event.reservationProcessId())
                .map(process -> apply(process, event, now))
                .orElseGet(PhoneRequestResult.ProcessNotFound::new);
    }

    private PhoneRequestResult apply(ReservationProcess process, PhoneRequestedEvent event, Instant now) {
        if (!process.holdId().equals(event.holdId())) {
            return new PhoneRequestResult.HoldMismatch();
        }
        return switch (process.status()) {
            // El evento puede llegar antes que el resultado del REST: cualquiera de los dos camina el proceso.
            case HELD -> applied(process.transitionTo(ReservationStatus.WAITING_FOR_PHONE, now), event, now);
            // Un pedido nuevo (por ejemplo tras un rechazo) reemplaza al anterior: el último es el que vale.
            case WAITING_FOR_PHONE -> applied(process, event, now);
            case PHONE_SUBMITTED, CONFIRMED, CANCELLED, EXPIRED, FAILED -> new PhoneRequestResult.NotApplicable();
        };
    }

    private PhoneRequestResult applied(ReservationProcess process, PhoneRequestedEvent event, Instant now) {
        return new PhoneRequestResult.Applied(
                repository.save(process.withPhoneRequestEventId(event.eventId(), now)));
    }
}
