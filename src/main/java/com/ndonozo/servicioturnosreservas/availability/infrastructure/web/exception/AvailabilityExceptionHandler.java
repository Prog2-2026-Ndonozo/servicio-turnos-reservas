package com.ndonozo.servicioturnosreservas.availability.infrastructure.web.exception;

import com.ndonozo.servicioturnosreservas.availability.application.exception.AgendaIntegrationException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.AgendaUnavailableException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.CatedraIntegrationException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.InvalidDateRangeException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.ProfessionalNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class AvailabilityExceptionHandler {

    @ExceptionHandler(InvalidDateRangeException.class)
    public ProblemDetail handle(InvalidDateRangeException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", e.getMessage());
    }

    @ExceptionHandler(ProfessionalNotFoundException.class)
    public ProblemDetail handle(ProfessionalNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "PROFESSIONAL_NOT_FOUND", e.getMessage());
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
        return problem(HttpStatus.BAD_GATEWAY, "CATEDRA_ERROR", "No se pudo completar la consulta a la cátedra");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
