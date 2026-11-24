package com.ndonozo.servicioturnosreservas.availability.infrastructure.web.controller;

import com.ndonozo.servicioturnosreservas.availability.application.service.AvailabilityService;
import com.ndonozo.servicioturnosreservas.availability.infrastructure.web.dto.AvailabilityRequest;
import com.ndonozo.servicioturnosreservas.availability.infrastructure.web.dto.AvailabilityResponse;
import com.ndonozo.servicioturnosreservas.availability.infrastructure.web.mapper.AvailabilityDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Disponibilidad", description = "Turnos libres de un profesional. Turnos combina los horarios semanales del "
        + "catálogo con las ocupaciones de la cátedra en cada pedido: no guarda ni cachea ninguno de los dos")
public class AvailabilityController {
    private final AvailabilityService availabilityService;
    private final AvailabilityDtoMapper mapper;

    @Operation(summary = "Turnos libres de un profesional",
            description = "Devuelve los turnos libres entre las dos fechas (inclusive), ordenados por fecha y hora. "
                    + "Se excluyen los turnos que no entran completos en un horario, los ocupados o retenidos y los que ya "
                    + "empezaron. Un profesional deshabilitado devuelve una lista vacía. Las horas son locales de atención.")
    @ApiResponse(responseCode = "200", description = "Turnos libres; la lista puede estar vacía")
    @ApiResponse(responseCode = "400", description = "Parámetro inválido (VALIDATION_ERROR) o rango inválido (INVALID_DATE_RANGE)")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido (UNAUTHENTICATED)")
    @ApiResponse(responseCode = "404", description = "El profesional no existe (PROFESSIONAL_NOT_FOUND)")
    @ApiResponse(responseCode = "502", description = "Error de integración con el catálogo o la cátedra (CATALOG_ERROR, CATEDRA_ERROR)")
    @ApiResponse(responseCode = "503", description = "El catálogo o la cátedra no están disponibles (CATALOG_UNAVAILABLE, CATEDRA_UNAVAILABLE)")
    @GetMapping("/availability")
    public AvailabilityResponse availability(@Valid @ParameterObject AvailabilityRequest request) {
        return mapper.toResponse(
                availabilityService.getAvailability(request.professionalId(), request.from(), request.to()));
    }
}