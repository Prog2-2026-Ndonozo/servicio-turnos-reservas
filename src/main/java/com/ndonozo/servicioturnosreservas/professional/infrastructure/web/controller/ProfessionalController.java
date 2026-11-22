package com.ndonozo.servicioturnosreservas.professional.infrastructure.web.controller;

import com.ndonozo.servicioturnosreservas.professional.application.service.ProfessionalService;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.web.dto.ProfessionalCategoryResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.web.dto.ProfessionalResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.web.dto.ProfessionalSearchRequest;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.web.mapper.ProfessionalDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Profesionales", description = "Busqueda de profesionales y categorias. Turnos renvia la consulta al " + "servicio de catalogo en cada pedido: no guarda ni cachea el catalogo")
public class ProfessionalController {
    private final ProfessionalService professionalService;
    private final ProfessionalDtoMapper mapper;

    @Operation(summary = "Busca profesionales",
            description = "Todos los filtros son opcionales y se combinan con AND. Sin filtros devuelve todos los "
                    + "profesionales, ordenados por apellido, nombre e id (el orden del catálogo). No hay paginación. "
                    + "availableOn es un prefiltro por día de la semana, no una consulta de turnos libres.")
    @ApiResponse(responseCode = "200", description = "Lista de profesionales; vacía si no hay coincidencias")
    @ApiResponse(responseCode = "400", description = "Filtro inválido (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido (UNAUTHENTICATED)")
    @ApiResponse(responseCode = "502", description = "Error de integración con el catálogo (CATALOG_ERROR)")
    @ApiResponse(responseCode = "503", description = "El catálogo no está disponible (CATALOG_UNAVAILABLE)")
    @GetMapping("/professionals/search")
    public List<ProfessionalResponse> search(@Valid @ParameterObject ProfessionalSearchRequest request) {
        return professionalService.search(mapper.toCriteria(request)).stream().map(mapper::toResponse).toList();
    }

    @Operation(summary = "Lista las categorías",
            description = "Devuelve todas las categorías, habilitadas o no, ordenadas por nombre e id. Sirve para "
                    + "ofrecer el filtro por categoría de la búsqueda.")
    @ApiResponse(responseCode = "200", description = "Lista de categorías")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido (UNAUTHENTICATED)")
    @ApiResponse(responseCode = "502", description = "Error de integración con el catálogo (CATALOG_ERROR)")
    @ApiResponse(responseCode = "503", description = "El catálogo no está disponible (CATALOG_UNAVAILABLE)")
    @GetMapping("/professional-categories")
    public List<ProfessionalCategoryResponse> categories() {
        return professionalService.categories().stream().map(mapper::toResponse).toList();
    }
}


