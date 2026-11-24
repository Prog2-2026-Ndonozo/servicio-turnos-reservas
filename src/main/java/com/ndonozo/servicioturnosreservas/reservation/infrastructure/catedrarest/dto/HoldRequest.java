package com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto;

public record HoldRequest(
        long professionalId,
        String date,
        String startTime,
        String externalPatientId
) {
}
