package com.ndonozo.servicioturnosreservas.reservation.application.usecases;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneNumber;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneSubmission;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationStatus;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.SubmitPhoneCommand;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.SubmitPhoneResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.in.SubmitPhoneUseCase;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.PhoneSubmissionPort;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmitPhoneUseCaseImpl implements SubmitPhoneUseCase {

    private final ReservationProcessRepository repository;
    private final PhoneSubmissionPort phonePort;
    private final Clock clock;

    // Sin @Transactional: cada guardado es su propia transacción y la publicación a Kafka queda fuera de ellas (ADR-0011).
    @Override
    public SubmitPhoneResult submitPhone(SubmitPhoneCommand command) {
        return PhoneNumber.parse(command.phoneNumber())
                .map(phone -> repository.findByReservationProcessIdAndUserId(command.reservationProcessId(), command.userId())
                        // Un proceso ajeno se trata igual que uno inexistente: no se revela que existe (E §9).
                        .map(process -> submit(process, phone))
                        .orElseGet(SubmitPhoneResult.NotFound::new))
                .orElseGet(SubmitPhoneResult.InvalidPhone::new);
    }

    private SubmitPhoneResult submit(ReservationProcess process, PhoneNumber phone) {
        if (process.status() != ReservationStatus.WAITING_FOR_PHONE) {
            return new SubmitPhoneResult.InvalidState();
        }
        Instant now = Instant.now(clock);
        if (!now.isBefore(process.expiresAt())) {
            // Un hold vencido no recibe teléfono: se cierra sin publicar nada (I §18.3).
            repository.save(process.transitionTo(ReservationStatus.EXPIRED, now));
            return new SubmitPhoneResult.HoldExpired();
        }
        if (process.phoneRequestEventId() == null) {
            // La confirmación REST llegó antes que el evento de Kafka: no hay requestEventId que devolver (ADR-0011).
            return new SubmitPhoneResult.PhoneNotRequested();
        }
        // Guardar primero: el resultado de la cátedra no puede llegar antes que este estado. Si dos pedidos compiten,
        // el segundo falla por la versión y no publica.
        ReservationProcess submitted = repository.save(process.transitionTo(ReservationStatus.PHONE_SUBMITTED, now));
        try {
            phonePort.submit(new PhoneSubmission("phone-submitted-" + UUID.randomUUID(), process.reservationProcessId(),
                    process.holdId(), process.phoneRequestEventId(), phone.value(), now));
        } catch (RuntimeException e) {
            revert(submitted, e);
            throw e;
        }
        return new SubmitPhoneResult.Submitted(submitted);
    }

    // Si no se pudo publicar el proceso vuelve a esperar el teléfono; si tampoco se puede guardar, lo reconcilia T12.
    private void revert(ReservationProcess submitted, RuntimeException cause) {
        try {
            repository.save(submitted.transitionTo(ReservationStatus.WAITING_FOR_PHONE, Instant.now(clock)));
        } catch (RuntimeException e) {
            log.error("No se pudo devolver el proceso {} a WAITING_FOR_PHONE tras fallar la publicación",
                    submitted.reservationProcessId(), e);
            cause.addSuppressed(e);
        }
    }
}
