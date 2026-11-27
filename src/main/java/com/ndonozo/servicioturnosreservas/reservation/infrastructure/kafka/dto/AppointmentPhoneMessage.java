package com.ndonozo.servicioturnosreservas.reservation.infrastructure.kafka.dto;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneSubmission;

import java.time.temporal.ChronoUnit;

/** AdditionalInformationSubmitted (I §15.5): todos los campos son obligatorios. */
public record AppointmentPhoneMessage(
        String eventId,
        String eventType,
        String groupId,
        String reservationProcessId,
        String holdId,
        String requestEventId,
        Information information,
        String occurredAt,
        int schemaVersion
) {
    static final String EVENT_TYPE = "AdditionalInformationSubmitted";
    static final int SCHEMA_VERSION = 1;

    public record Information(String phoneNumber) {}

    public static AppointmentPhoneMessage of(String groupId, PhoneSubmission submission) {
        return new AppointmentPhoneMessage(submission.eventId(), EVENT_TYPE, groupId, submission.reservationProcessId(),
                submission.holdId(), submission.requestEventId(), new Information(submission.phoneNumber()),
                // Texto ISO-8601 en segundos, como el ejemplo de I §15.5, sin depender de cómo configure Jackson las fechas
                submission.occurredAt().truncatedTo(ChronoUnit.SECONDS).toString(), SCHEMA_VERSION);
    }
}
