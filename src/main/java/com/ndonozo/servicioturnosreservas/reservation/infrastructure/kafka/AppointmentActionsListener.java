package com.ndonozo.servicioturnosreservas.reservation.infrastructure.kafka;

import com.ndonozo.servicioturnosreservas.reservation.application.service.ReservationService;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.AppointmentResultEvent;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.AppointmentResultOutcome;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneRequestResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneRequestedEvent;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.kafka.dto.AppointmentActionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Adaptador de entrada: el topic de acciones trae pedidos y resultados de reserva. El mensaje llega como texto, se
 * parsea acá y lo ilegible o ajeno se descarta; lo operativo (base caída, choque de versión) se deja propagar para que
 * Spring Kafka reintente (KafkaErrorHandlingConfig).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentActionsListener {

    static final String PHONE_REQUESTED = "AdditionalInformationRequested";
    static final String PHONE_NUMBER = "PHONE_NUMBER";
    static final String CONFIRMED = "AppointmentConfirmed";
    static final String REJECTED = "AdditionalInformationRejected";
    static final String EXPIRED = "AppointmentProcessExpired";
    static final String INVALID = "AppointmentProcessInvalid";
    private static final int MAX_REASON_LENGTH = 100;

    private final JsonMapper jsonMapper;
    private final ReservationService reservationService;

    @KafkaListener(topics = "${app.catedra.kafka.appointment-actions-topic}")
    public void onMessage(String payload) {
        AppointmentActionMessage message;
        try {
            message = jsonMapper.readValue(payload, AppointmentActionMessage.class);
        } catch (JacksonException e) {
            log.warn("Mensaje del topic de acciones descartado: no es JSON válido");
            return;
        }
        if (message == null || message.eventType() == null) {
            log.debug("Mensaje del topic de acciones ignorado: sin eventType");
            return;
        }
        switch (message.eventType()) {
            case PHONE_REQUESTED -> onPhoneRequested(message);
            case CONFIRMED, REJECTED, EXPIRED, INVALID -> onResult(message);
            // La cancelación (I §15.10) y los tipos desconocidos no cambian el proceso de reserva.
            default -> log.debug("Evento {} ignorado (eventId={})", message.eventType(), message.eventId());
        }
    }

    private void onPhoneRequested(AppointmentActionMessage message) {
        if (!PHONE_NUMBER.equals(message.requiredField()) || !hasIdentifiers(message)) {
            log.warn("AdditionalInformationRequested descartado: faltan datos o no pide un teléfono (eventId={})", message.eventId());
            return;
        }
        PhoneRequestResult result = reservationService.handlePhoneRequested(
                new PhoneRequestedEvent(message.eventId(), message.reservationProcessId(), message.holdId()));
        switch (result) {
            case PhoneRequestResult.Applied applied -> log.info("Pedido de teléfono aplicado (eventId={}, reservationProcessId={})",
                    message.eventId(), message.reservationProcessId());
            case PhoneRequestResult.Duplicate duplicate -> log.info("Pedido de teléfono repetido, sin efecto (eventId={})", message.eventId());
            case PhoneRequestResult.ProcessNotFound notFound -> log.warn("Pedido de teléfono de un proceso desconocido (eventId={}, reservationProcessId={})",
                    message.eventId(), message.reservationProcessId());
            case PhoneRequestResult.HoldMismatch mismatch -> log.warn("Pedido de teléfono con otro holdId (eventId={}, reservationProcessId={})",
                    message.eventId(), message.reservationProcessId());
            case PhoneRequestResult.NotApplicable notApplicable -> log.info("Pedido de teléfono sin efecto: el proceso ya no lo espera (eventId={})",
                    message.eventId());
        }
    }

    private void onResult(AppointmentActionMessage message) {
        AppointmentResultEvent event = toResultEvent(message);
        if (event == null) {
            log.warn("{} descartado: faltan datos (eventId={})", message.eventType(), message.eventId());
            return;
        }
        AppointmentResultOutcome outcome = reservationService.handleAppointmentResult(event);
        switch (outcome) {
            case AppointmentResultOutcome.Applied applied -> log.info("{} aplicado (eventId={}, reservationProcessId={}, estado={})",
                    message.eventType(), message.eventId(), message.reservationProcessId(), applied.process().status());
            case AppointmentResultOutcome.Duplicate duplicate -> log.info("{} repetido, sin efecto (eventId={})",
                    message.eventType(), message.eventId());
            case AppointmentResultOutcome.ProcessNotFound notFound -> log.warn("{} de un proceso desconocido (eventId={}, reservationProcessId={})",
                    message.eventType(), message.eventId(), message.reservationProcessId());
            case AppointmentResultOutcome.HoldMismatch mismatch -> log.warn("{} con otro holdId (eventId={}, reservationProcessId={})",
                    message.eventType(), message.eventId(), message.reservationProcessId());
            case AppointmentResultOutcome.NotApplicable notApplicable -> log.info("{} sin efecto: el proceso no estaba en un estado que lo admita (eventId={})",
                    message.eventType(), message.eventId());
        }
    }

    /** Null si faltan los datos que el resultado necesita (el mensaje se descarta con un WARN). */
    private static AppointmentResultEvent toResultEvent(AppointmentActionMessage message) {
        if (!hasIdentifiers(message)) {
            return null;
        }
        String id = message.eventId();
        String process = message.reservationProcessId();
        String hold = message.holdId();
        return switch (message.eventType()) {
            case CONFIRMED -> message.reservationId() == null ? null
                    : new AppointmentResultEvent.Confirmed(id, process, hold, message.reservationId());
            case REJECTED -> new AppointmentResultEvent.Rejected(id, process, hold, abbreviate(message.reason()),
                    parseInstant(message.expiresAt()));
            case EXPIRED -> new AppointmentResultEvent.Expired(id, process, hold);
            case INVALID -> new AppointmentResultEvent.Invalid(id, process, hold);
            default -> null;
        };
    }

    private static boolean hasIdentifiers(AppointmentActionMessage message) {
        return !isBlank(message.eventId()) && !isBlank(message.reservationProcessId()) && !isBlank(message.holdId());
    }

    private static String abbreviate(String reason) {
        if (isBlank(reason)) {
            return null;
        }
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }

    private static Instant parseInstant(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
