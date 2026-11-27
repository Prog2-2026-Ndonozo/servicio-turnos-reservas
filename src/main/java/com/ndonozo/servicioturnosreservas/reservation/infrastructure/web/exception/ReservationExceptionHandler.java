package com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.exception;

import com.ndonozo.servicioturnosreservas.reservation.application.exception.AgendaIntegrationException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.AgendaUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraIntegrationException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.HoldExpiredException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.InvalidReservationStateException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.InvalidSlotException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.ProfessionalDisabledException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.ProfessionalNotFoundException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.ReservationNotFoundException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.SlotAlreadyHeldException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.SlotAlreadyReservedException;
import com.ndonozo.servicioturnosreservas.reservation.domain.exception.ReservationConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ReservationExceptionHandler {

    @ExceptionHandler(InvalidSlotException.class)
    public ProblemDetail handle(InvalidSlotException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_SLOT", e.getMessage());
    }

    @ExceptionHandler(ProfessionalDisabledException.class)
    public ProblemDetail handle(ProfessionalDisabledException e) {
        return problem(HttpStatus.BAD_REQUEST, "PROFESSIONAL_DISABLED", e.getMessage());
    }

    @ExceptionHandler(ProfessionalNotFoundException.class)
    public ProblemDetail handle(ProfessionalNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "PROFESSIONAL_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(SlotAlreadyHeldException.class)
    public ProblemDetail handle(SlotAlreadyHeldException e) {
        return problem(HttpStatus.CONFLICT, "SLOT_ALREADY_HELD", e.getMessage());
    }

    @ExceptionHandler(SlotAlreadyReservedException.class)
    public ProblemDetail handle(SlotAlreadyReservedException e) {
        return problem(HttpStatus.CONFLICT, "SLOT_ALREADY_RESERVED", e.getMessage());
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ProblemDetail handle(ReservationNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(InvalidReservationStateException.class)
    public ProblemDetail handle(InvalidReservationStateException e) {
        return problem(HttpStatus.CONFLICT, "INVALID_RESERVATION_STATE", e.getMessage());
    }

    @ExceptionHandler(HoldExpiredException.class)
    public ProblemDetail handle(HoldExpiredException e) {
        return problem(HttpStatus.CONFLICT, "HOLD_EXPIRED", e.getMessage());
    }

    @ExceptionHandler(ReservationConflictException.class)
    public ProblemDetail handle(ReservationConflictException e) {
        log.warn("Choque de versión sobre un proceso de reserva: {}", e.getMessage());
        return problem(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "La reserva cambió mientras se procesaba. Intentá de nuevo");
    }

    @ExceptionHandler(AgendaUnavailableException.class)
    public ProblemDetail handle(AgendaUnavailableException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE",
                "El catálogo no está disponible en este momento. Intentá de nuevo más tarde");
    }

    @ExceptionHandler(AgendaIntegrationException.class)
    public ProblemDetail handle(AgendaIntegrationException e) {
        return problem(HttpStatus.BAD_GATEWAY, "CATALOG_ERROR", "No se pudo completar la consulta al catálogo");
    }

    @ExceptionHandler(CatedraUnavailableException.class)
    public ProblemDetail handle(CatedraUnavailableException e) {
        log.warn("La cátedra no está disponible: {}", e.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "CATEDRA_UNAVAILABLE",
                "La cátedra no está disponible en este momento. Intentá de nuevo más tarde");
    }

    @ExceptionHandler(CatedraIntegrationException.class)
    public ProblemDetail handle(CatedraIntegrationException e) {
        log.error("Falló la integración con la cátedra: {}", e.getMessage(), e);
        return problem(HttpStatus.BAD_GATEWAY, "CATEDRA_ERROR", "No se pudo completar la operación con la cátedra");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
