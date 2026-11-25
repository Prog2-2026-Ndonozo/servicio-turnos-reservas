package com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.controller;

import com.ndonozo.servicioturnosreservas.reservation.application.service.ReservationService;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.dto.ConfirmReservationRequest;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.dto.CreateReservationRequest;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.dto.ReservationResponse;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.mapper.ReservationDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Reservas", description = "Inicio de la reserva de un turno. Turnos valida el turno con el catálogo y le "
        + "pide a la cátedra que lo retenga unos minutos; los pasos siguientes confirman la reserva")
public class ReservationController {
    private final ReservationService reservationService;
    private final ReservationDtoMapper mapper;

    @Operation(summary = "Reserva un turno (crea el hold)",
            description = "Retiene el turno para el usuario autenticado hasta expiresAt. Si el turno no existe en la agenda "
                    + "vigente del profesional, ya empezó o no está alineado con sus horarios, responde INVALID_SLOT. "
                    + "No se reintenta solo: ante un 503 el usuario decide si repite.")
    @ApiResponse(responseCode = "201", description = "Turno retenido; el proceso queda en estado HELD")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (VALIDATION_ERROR, MALFORMED_REQUEST), turno fuera de la "
            + "agenda (INVALID_SLOT) o profesional deshabilitado (PROFESSIONAL_DISABLED)")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido (UNAUTHENTICATED)")
    @ApiResponse(responseCode = "404", description = "El profesional no existe (PROFESSIONAL_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "El turno ya está retenido (SLOT_ALREADY_HELD) o reservado (SLOT_ALREADY_RESERVED)")
    @ApiResponse(responseCode = "502", description = "Error de integración con el catálogo o la cátedra (CATALOG_ERROR, CATEDRA_ERROR)")
    @ApiResponse(responseCode = "503", description = "El catálogo o la cátedra no están disponibles (CATALOG_UNAVAILABLE, CATEDRA_UNAVAILABLE)")
    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateReservationRequest request) {
        return mapper.toResponse(reservationService.createReservation(mapper.toCommand(userId(jwt), request)));
    }

    @Operation(summary = "Confirma el turno retenido",
            description = "Confirma inicialmente el hold con el nombre y el apellido del paciente. Si el proceso ya esperaba el "
                    + "teléfono, responde igual (es idempotente). Pasado expiresAt no se puede confirmar. No se reintenta solo: "
                    + "ante un 503 el usuario repite y la cátedra informa si ya estaba confirmado.")
    @ApiResponse(responseCode = "202", description = "Confirmación aceptada; el proceso queda en WAITING_FOR_PHONE")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (VALIDATION_ERROR, MALFORMED_REQUEST)")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido (UNAUTHENTICATED)")
    @ApiResponse(responseCode = "404", description = "La reserva no existe o no es del usuario (RESERVATION_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "El turno venció (HOLD_EXPIRED), ya lo reservó otro (SLOT_ALREADY_RESERVED) "
            + "o el proceso no está en un estado que se pueda confirmar (INVALID_RESERVATION_STATE)")
    @ApiResponse(responseCode = "502", description = "Error de integración con la cátedra (CATEDRA_ERROR)")
    @ApiResponse(responseCode = "503", description = "La cátedra no está disponible (CATEDRA_UNAVAILABLE)")
    @PostMapping("/reservations/{reservationProcessId}/confirm")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReservationResponse confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable String reservationProcessId,
                                       @Valid @RequestBody ConfirmReservationRequest request) {
        return mapper.toResponse(reservationService.confirmReservation(
                mapper.toCommand(userId(jwt), reservationProcessId, request)));
    }

    // El id sale del claim uid que emite este mismo servicio (ADR-0003). Sin él el token no sirve para reservar.
    private static long userId(Jwt jwt) {
        if (jwt.getClaim("uid") instanceof Number uid) {
            return uid.longValue();
        }
        throw new InsufficientAuthenticationException("El token no trae el identificador del usuario");
    }
}
