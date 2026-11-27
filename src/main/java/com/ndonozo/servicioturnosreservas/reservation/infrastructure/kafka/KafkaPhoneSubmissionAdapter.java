package com.ndonozo.servicioturnosreservas.reservation.infrastructure.kafka;

import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneSubmission;
import com.ndonozo.servicioturnosreservas.reservation.domain.ports.out.PhoneSubmissionPort;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.kafka.dto.AppointmentPhoneMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class KafkaPhoneSubmissionAdapter implements PhoneSubmissionPort {

    private static final long CONFIRMATION_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper jsonMapper;
    private final String topic;
    private final String groupId;

    public KafkaPhoneSubmissionAdapter(KafkaTemplate<String, String> kafka, JsonMapper jsonMapper,
                                       @Value("${app.catedra.kafka.appointment-phone-topic}") String topic,
                                       @Value("${app.catedra.group-id}") String groupId) {
        this.kafka = kafka;
        this.jsonMapper = jsonMapper;
        this.topic = topic;
        this.groupId = groupId;
    }

    @Override
    public void submit(PhoneSubmission submission) {
        String payload = jsonMapper.writeValueAsString(AppointmentPhoneMessage.of(groupId, submission));
        try {
            // La key es el reservationProcessId: los eventos de un mismo proceso conservan el orden (I §15.1).
            kafka.send(topic, submission.reservationProcessId(), payload).get(CONFIRMATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatedraUnavailableException("Se interrumpió el envío del teléfono a la cátedra", e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new CatedraUnavailableException("No se pudo publicar el teléfono en Kafka", e);
        }
        // No se registra el teléfono: es un dato personal.
        log.info("Teléfono enviado a la cátedra (eventId={}, reservationProcessId={})", submission.eventId(),
                submission.reservationProcessId());
    }
}
