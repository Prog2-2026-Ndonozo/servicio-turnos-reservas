package com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatedraProblem(
        String code
) {
}
