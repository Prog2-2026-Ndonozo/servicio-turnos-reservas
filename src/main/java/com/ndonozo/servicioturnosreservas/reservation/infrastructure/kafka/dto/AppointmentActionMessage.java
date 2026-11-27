package com.ndonozo.servicioturnosreservas.reservation.infrastructure.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Lo que turnos lee de los mensajes del topic de acciones. El resto de los campos (I §15.4) se ignora. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppointmentActionMessage(
        String eventId,
        String eventType,
        String reservationProcessId,
        String holdId,
        String requiredField
) {
}
