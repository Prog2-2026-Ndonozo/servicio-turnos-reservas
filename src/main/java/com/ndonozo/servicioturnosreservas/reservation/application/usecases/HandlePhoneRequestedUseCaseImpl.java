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

/**
 * Caso de uso disparado por Kafka (no por HTTP): la cátedra avisa que necesita el teléfono del paciente
 * (AdditionalInformationRequested, I §15.4).
 * <p>
 * Qué hace: pasa el proceso de HELD a WAITING_FOR_PHONE y guarda el eventId del pedido, que después
 * {@link SubmitPhoneUseCaseImpl} devuelve a la cátedra como requestEventId.
 * <p>
 * Forma general (la misma de los casos de uso de eventos): evitar duplicados, buscar el proceso, decidir según su
 * estado, guardar y devolver un resultado (nunca lanza excepciones de negocio).
 */
@Component
@RequiredArgsConstructor
public class HandlePhoneRequestedUseCaseImpl implements HandlePhoneRequestedUseCase {

    // Puerto de salida: registro de eventId ya procesados (tabla processed_event). Da la idempotencia.
    private final ProcessedEventRepository processedEvents;
    // Puerto de salida: lectura y guardado de procesos de reserva.
    private final ReservationProcessRepository repository;
    // Reloj inyectado: en los tests se reemplaza por uno fijo.
    private final Clock clock;

    /**
     * Todo en una transacción: si guardar falla (por ejemplo, un choque de versión), el eventId tampoco queda registrado
     * y el reintento de Spring Kafka vuelve a intentar con el estado anterior intacto (ADR-0010).
     */
    @Override
    @Transactional
    public PhoneRequestResult handlePhoneRequested(PhoneRequestedEvent event) {
        Instant now = Instant.now(clock);
        // 1. Idempotencia: Kafka puede entregar dos veces el mismo mensaje. Si el eventId ya estaba registrado,
        //    markProcessed devuelve false y no se hace nada más.
        if (!processedEvents.markProcessed(event.eventId(), now)) {
            return new PhoneRequestResult.Duplicate();
        }
        // 2. Se busca el proceso por su id (sin filtrar por usuario: el evento viene de la cátedra, no de un usuario).
        //    Si no existe, se devuelve ProcessNotFound y el listener lo registra.
        return repository.findByReservationProcessId(event.reservationProcessId())
                .map(process -> apply(process, event, now))
                .orElseGet(PhoneRequestResult.ProcessNotFound::new);
    }

    private PhoneRequestResult apply(ReservationProcess process, PhoneRequestedEvent event, Instant now) {
        // 3. Control de seguridad: el evento tiene que ser del mismo hold que guardamos.
        if (!process.holdId().equals(event.holdId())) {
            return new PhoneRequestResult.HoldMismatch();
        }
        // 4. Qué se hace depende del estado actual del proceso (máquina de estados).
        return switch (process.status()) {
            // El evento puede llegar antes que el resultado del REST: cualquiera de los dos camina el proceso.
            case HELD -> applied(process.transitionTo(ReservationStatus.WAITING_FOR_PHONE, now), event, now);
            // Un pedido nuevo (por ejemplo tras un rechazo) reemplaza al anterior: el último es el que vale.
            case WAITING_FOR_PHONE -> applied(process, event, now);
            // Cualquier otro estado: el pedido ya no tiene efecto (por ejemplo, el teléfono ya se envió o el proceso cerró).
            case PHONE_SUBMITTED, CONFIRMED, CANCELLED, EXPIRED, FAILED -> new PhoneRequestResult.NotApplicable();
        };
    }

    // Guarda el proceso con el eventId del pedido. Ese eventId es el requestEventId que SubmitPhone necesita después.
    private PhoneRequestResult applied(ReservationProcess process, PhoneRequestedEvent event, Instant now) {
        return new PhoneRequestResult.Applied(
                repository.save(process.withPhoneRequestEventId(event.eventId(), now)));
    }
}
