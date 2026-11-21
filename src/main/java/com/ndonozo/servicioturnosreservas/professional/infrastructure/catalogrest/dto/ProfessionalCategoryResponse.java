package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfessionalCategoryResponse(Long id, String name, String description, boolean enabled) {}