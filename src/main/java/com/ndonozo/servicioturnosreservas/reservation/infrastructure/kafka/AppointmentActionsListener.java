package com.ndonozo.servicioturnosreservas.reservation.infrastructure.kafka;

import com.ndonozo.servicioturnosreservas.reservation.application.service.ReservationService;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneRequestResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneRequestedEvent;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.kafka.dto.AppointmentActionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

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
        if (message == null || !PHONE_REQUESTED.equals(message.eventType())) {
            // Los resultados (confirmación, rechazo, expiración...) se tratan en T9.
            log.debug("Evento {} ignorado (eventId={})", message == null ? null : message.eventType(),
                    message == null ? null : message.eventId());
            return;
        }
        if (!PHONE_NUMBER.equals(message.requiredField()) || isBlank(message.eventId())
                || isBlank(message.reservationProcessId()) || isBlank(message.holdId())) {
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
