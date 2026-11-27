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

/**
 * Caso de uso disparado por Kafka: la cátedra publica el resultado final del proceso (I §15.6 a §15.9).
 * <p>
 * Qué hace: según el tipo de resultado, actualiza el estado del proceso.
 * <ul>
 *   <li>Confirmed: PHONE_SUBMITTED pasa a CONFIRMED y se guarda el reservation_id.</li>
 *   <li>Rejected (teléfono rechazado): vuelve a WAITING_FOR_PHONE, con el motivo y el nuevo vencimiento.</li>
 *   <li>Expired: pasa a EXPIRED.</li>
 *   <li>Invalid: pasa a FAILED.</li>
 * </ul>
 * Un estado final (CONFIRMED, EXPIRED, FAILED, CANCELLED) nunca retrocede, aunque el evento llegue tarde o repetido.
 */
@Component
@RequiredArgsConstructor
public class HandleAppointmentResultUseCaseImpl implements HandleAppointmentResultUseCase {

    // Puerto de salida: registro de eventId ya procesados (idempotencia).
    private final ProcessedEventRepository processedEvents;
    // Puerto de salida: procesos de reserva.
    private final ReservationProcessRepository repository;
    private final Clock clock;

    /** Una sola transacción, como el pedido de teléfono: si guardar falla, el eventId tampoco queda registrado (ADR-0010). */
    @Override
    @Transactional
    public AppointmentResultOutcome handleResult(AppointmentResultEvent event) {
        Instant now = Instant.now(clock);
        // 1. Idempotencia: si el eventId ya se procesó, es un duplicado y no se hace nada.
        if (!processedEvents.markProcessed(event.eventId(), now)) {
            return new AppointmentResultOutcome.Duplicate();
        }
        // 2. Se busca el proceso por su id; si no existe, ProcessNotFound.
        return repository.findByReservationProcessId(event.reservationProcessId())
                .map(process -> apply(process, event, now))
                .orElseGet(AppointmentResultOutcome.ProcessNotFound::new);
    }

    private AppointmentResultOutcome apply(ReservationProcess process, AppointmentResultEvent event, Instant now) {
        // 3. El evento debe ser del mismo hold que guardamos; si no, se descarta.
        if (!process.holdId().equals(event.holdId())) {
            return new AppointmentResultOutcome.HoldMismatch();
        }
        // La máquina de estados decide qué se puede mover: un estado final o CONFIRMED no se altera (E §7, I §18.3).
        // 4. Cada tipo de resultado tiene su regla; si el estado actual no la admite, el resultado es NotApplicable.
        return switch (event) {
            // Confirmada: solo desde PHONE_SUBMITTED. Queda CONFIRMED con el reservation_id (lo usará cancelar, T11).
            case AppointmentResultEvent.Confirmed confirmed -> process.status() == ReservationStatus.PHONE_SUBMITTED
                    ? applied(process.confirmedAs(confirmed.reservationId(), now))
                    : new AppointmentResultOutcome.NotApplicable();
            // Teléfono rechazado: vuelve a WAITING_FOR_PHONE con el motivo y el nuevo expiresAt, para reintentar.
            case AppointmentResultEvent.Rejected rejected -> process.status() == ReservationStatus.PHONE_SUBMITTED
                    ? applied(process.rejectedWith(rejected.reason(), rejected.expiresAt(), now))
                    : new AppointmentResultOutcome.NotApplicable();
            // Vencido: pasa a EXPIRED solo si la máquina de estados lo permite desde el estado actual.
            case AppointmentResultEvent.Expired expired -> process.status().canTransitionTo(ReservationStatus.EXPIRED)
                    ? applied(process.transitionTo(ReservationStatus.EXPIRED, now))
                    : new AppointmentResultOutcome.NotApplicable();
            // Inválido: pasa a FAILED, con la misma condición.
            case AppointmentResultEvent.Invalid invalid -> process.status().canTransitionTo(ReservationStatus.FAILED)
                    ? applied(process.transitionTo(ReservationStatus.FAILED, now))
                    : new AppointmentResultOutcome.NotApplicable();
        };
    }

    private AppointmentResultOutcome applied(ReservationProcess process) {
        return new AppointmentResultOutcome.Applied(repository.save(process));
    }
}
