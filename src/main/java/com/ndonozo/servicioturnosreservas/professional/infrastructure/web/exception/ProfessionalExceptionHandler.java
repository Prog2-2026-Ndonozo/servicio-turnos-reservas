package com.ndonozo.servicioturnosreservas.professional.infrastructure.web.exception;

import com.ndonozo.servicioturnosreservas.professional.application.exception.CatalogIntegrationException;
import com.ndonozo.servicioturnosreservas.professional.application.exception.CatalogUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Los mensajes que salen hacia la app son genéricos: nunca llevan la URL del catálogo, el token ni detalles internos.
 * El detalle real queda en el registro.
 */
@Slf4j
@RestControllerAdvice
public class ProfessionalExceptionHandler {

    @ExceptionHandler(CatalogUnavailableException.class)
    public ProblemDetail handle(CatalogUnavailableException e) {
        log.warn("El catálogo no está disponible: {}", e.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE",
                "El catálogo no está disponible en este momento. Intentá de nuevo más tarde");
    }

    @ExceptionHandler(CatalogIntegrationException.class)
    public ProblemDetail handle(CatalogIntegrationException e) {
        log.error("Falló la integración con el catálogo: {}", e.getMessage(), e);
        return problem(HttpStatus.BAD_GATEWAY, "CATALOG_ERROR", "No se pudo completar la consulta al catálogo");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
