package com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto;

public record ConfirmHoldRequest(
        String externalPatientId,
        String patientFirstName,
        String patientLastName
) {
}
