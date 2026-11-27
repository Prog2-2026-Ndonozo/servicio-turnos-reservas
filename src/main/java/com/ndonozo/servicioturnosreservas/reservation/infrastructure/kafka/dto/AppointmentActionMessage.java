package com.ndonozo.servicioturnosreservas.reservation.infrastructure.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lo que turnos lee de los mensajes del topic de acciones (I §15.4 a §15.9): los campos comunes y los propios de cada
 * evento. {@code expiresAt} es texto a propósito: un valor ilegible no puede hacer descartar todo el resultado.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppointmentActionMessage(
        String eventId,
        String eventType,
        String reservationProcessId,
        String holdId,
        String requiredField,
        Long reservationId,
        String reason,
        String expiresAt
) {
}
