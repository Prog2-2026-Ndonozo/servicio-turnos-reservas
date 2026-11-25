package com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.client;

import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraIntegrationException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.CatedraProblem;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.HoldRequest;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.HoldResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class CatedraHoldRestClient {
    static final Set<String> BUSINESS_CODES = Set.of("SLOT_ALREADY_HELD", "SLOT_ALREADY_RESERVED", "INVALID_SLOT",
            "PROFESSIONAL_DISABLED", "PROFESSIONAL_NOT_FOUND");

    // Bean compartido de config/: ya lleva la URL base, los tiempos de espera y el JWT técnico.
    private final RestClient restClient;

    /** Un solo intento: la cátedra no tiene clave de idempotencia y repetir un POST con timeout podría crear otro hold. */
    public HoldResponse createHold(HoldRequest request) {
        try {
            HoldResponse body = restClient.post()
                    .uri("/api/appointment-holds")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(HoldResponse.class);
            if (body == null || isBlank(body.holdId()) || isBlank(body.reservationProcessId())
                    || body.expiresAt() == null || !"HELD".equals(body.status())) {
                throw new CatedraIntegrationException("La cátedra respondió un hold incompleto o en otro estado");
            }
            return body;
        } catch (HttpClientErrorException e) {
            String code = problemCode(e);
            if (code != null && BUSINESS_CODES.contains(code)) {
                throw new CatedraHoldRejectedException(code);
            }
            // 401 y 403 (JWT técnico) o un VALIDATION_ERROR: el JWT no se renueva y el pedido ya se validó, así que es
            // un error de integración.
            throw new CatedraIntegrationException("La cátedra respondió HTTP " + e.getStatusCode().value()
                    + (code == null ? "" : " (" + code + ")"), e);
        } catch (HttpServerErrorException e) {
            throw new CatedraUnavailableException("La cátedra respondió HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            // Sin conexión, conexión rechazada o se pasó el tiempo de espera: no se sabe si el hold se creó.
            throw new CatedraUnavailableException("No se pudo hablar con la cátedra: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new CatedraIntegrationException("No se pudo leer la respuesta de la cátedra", e);
        }
    }

    private static String problemCode(HttpClientErrorException e) {
        try {
            CatedraProblem problem = e.getResponseBodyAs(CatedraProblem.class);
            return problem == null ? null : problem.code();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
