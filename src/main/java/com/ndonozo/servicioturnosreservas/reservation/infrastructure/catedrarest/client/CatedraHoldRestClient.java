package com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.client;

import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraIntegrationException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.CatedraProblem;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.ConfirmHoldRequest;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.ConfirmHoldResponse;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.HoldRequest;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.HoldResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import java.util.Set;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class CatedraHoldRestClient {
    static final Set<String> CREATE_BUSINESS_CODES = Set.of("SLOT_ALREADY_HELD", "SLOT_ALREADY_RESERVED", "INVALID_SLOT",
            "PROFESSIONAL_DISABLED", "PROFESSIONAL_NOT_FOUND");

    // Al confirmar solo estos códigos son una respuesta de negocio; el resto (paciente o dueño distinto...) es un error de integración.
    static final Set<String> CONFIRM_BUSINESS_CODES = Set.of("HOLD_EXPIRED", "HOLD_NOT_FOUND",
            "RESERVATION_PROCESS_ALREADY_CONFIRMED", "SLOT_ALREADY_RESERVED");

    // Bean compartido de config/: ya lleva la URL base, los tiempos de espera y el JWT técnico.
    private final RestClient restClient;

    /** Un solo intento: la cátedra no tiene clave de idempotencia y repetir un POST con timeout podría crear otro hold. */
    public HoldResponse createHold(HoldRequest request) {
        return execute(CREATE_BUSINESS_CODES, () -> {
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
        });
    }

    /** Un solo intento: ante un timeout el usuario repite y la cátedra responde RESERVATION_PROCESS_ALREADY_CONFIRMED. */
    public ConfirmHoldResponse confirmHold(String holdId, ConfirmHoldRequest request) {
        return execute(CONFIRM_BUSINESS_CODES, () -> {
            ConfirmHoldResponse body = restClient.post()
                    .uri("/api/appointment-holds/{holdId}/confirm", holdId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ConfirmHoldResponse.class);
            if (body == null || !"WAITING_FOR_PHONE".equals(body.status())) {
                throw new CatedraIntegrationException("La cátedra respondió una confirmación vacía o en otro estado");
            }
            return body;
        });
    }

    private <T> T execute(Set<String> businessCodes, Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException e) {
            String code = problemCode(e);
            if (code != null && businessCodes.contains(code)) {
                throw new CatedraHoldRejectedException(code);
            }
            // 401 y 403 (JWT técnico) o un VALIDATION_ERROR: el JWT no se renueva y el pedido ya se validó, así que es
            // un error de integración.
            throw new CatedraIntegrationException("La cátedra respondió HTTP " + e.getStatusCode().value()
                    + (code == null ? "" : " (" + code + ")"), e);
        } catch (HttpServerErrorException e) {
            throw new CatedraUnavailableException("La cátedra respondió HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            // Sin conexión, conexión rechazada o se pasó el tiempo de espera: no se sabe si la operación se hizo.
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
