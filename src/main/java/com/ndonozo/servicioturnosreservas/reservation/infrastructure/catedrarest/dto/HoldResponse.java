package com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HoldResponse(
        String holdId,
        String reservationProcessId,
        String status,
        Instant expiresAt
) {
}
