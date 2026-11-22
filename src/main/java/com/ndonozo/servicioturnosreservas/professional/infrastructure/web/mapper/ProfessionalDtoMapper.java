package com.ndonozo.servicioturnosreservas.professional.infrastructure.web.mapper;

import com.ndonozo.servicioturnosreservas.professional.domain.model.Professional;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalCategory;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalSearchCriteria;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.web.dto.ProfessionalCategoryResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.web.dto.ProfessionalResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.web.dto.ProfessionalSearchRequest;
import org.springframework.stereotype.Component;

@Component
public class ProfessionalDtoMapper {

    public ProfessionalSearchCriteria toCriteria(ProfessionalSearchRequest request) {
        // Un nombre en blanco equivale a no filtrar (igual que en el catálogo); no se agrega ningún otro valor por defecto.
        String name = request.name() == null || request.name().isBlank() ? null : request.name();
        return new ProfessionalSearchCriteria(request.categoryId(), name, request.enabled(), request.availableOn());
    }

    public ProfessionalResponse toResponse(Professional professional) {
        return new ProfessionalResponse(professional.getId(), professional.getCategoryId(),
                professional.getFirstName(), professional.getLastName(), professional.isEnabled());
    }

    public ProfessionalCategoryResponse toResponse(ProfessionalCategory category) {
        return new ProfessionalCategoryResponse(category.getId(), category.getName(), category.getDescription(),
                category.isEnabled());
    }
}