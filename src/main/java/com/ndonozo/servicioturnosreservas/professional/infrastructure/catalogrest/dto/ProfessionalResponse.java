package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfessionalResponse(Long id, Long categoryId, String firstName, String lastName, boolean enabled) {}
