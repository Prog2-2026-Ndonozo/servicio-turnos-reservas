package com.ndonozo.servicioturnosreservas.reservation.infrastructure.kafka;

import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneSubmission;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El mensaje que turnos le envía a la cátedra (I §15.5): topic, key y los nueve campos, sobre un broker embebido. */
@SpringJUnitConfig
@EmbeddedKafka(partitions = 1, topics = KafkaPhoneSubmissionAdapterTest.TOPIC)
class KafkaPhoneSubmissionAdapterTest {

    static final String TOPIC = "catedra.turnos.telefono.prueba-adaptador";
    private static final PhoneSubmission ENVIO = new PhoneSubmission("phone-submitted-1", "process-1", "hold-1",
            "request-event-1", "+5492615551234", Instant.parse("2026-11-27T12:00:00.987654Z"));

    @Autowired
    private EmbeddedKafkaBroker broker;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void publica_en_el_topic_de_telefono_con_el_reservationProcessId_como_key_y_los_campos_de_I_15_5() throws Exception {
        KafkaTemplate<String, String> kafka = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));

        new KafkaPhoneSubmissionAdapter(kafka, jsonMapper, TOPIC, "proyecto-juan-perez").submit(ENVIO);

        ConsumerRecord<String, String> registro = leerUno();
        assertThat(registro.key()).isEqualTo("process-1");
        JsonNode json = jsonMapper.readTree(registro.value());
        List<String> campos = new ArrayList<>();
        json.propertyNames().forEach(campos::add);
        assertThat(campos).containsExactlyInAnyOrder("eventId", "eventType", "groupId", "reservationProcessId", "holdId",
                "requestEventId", "information", "occurredAt", "schemaVersion");
        assertThat(json.get("eventId").asString()).isEqualTo("phone-submitted-1");
        assertThat(json.get("eventType").asString()).isEqualTo("AdditionalInformationSubmitted");
        assertThat(json.get("groupId").asString()).isEqualTo("proyecto-juan-perez");
        assertThat(json.get("reservationProcessId").asString()).isEqualTo("process-1");
        assertThat(json.get("holdId").asString()).isEqualTo("hold-1");
        assertThat(json.get("requestEventId").asString()).isEqualTo("request-event-1");
        assertThat(json.get("information").get("phoneNumber").asString()).isEqualTo("+5492615551234");
        assertThat(json.get("occurredAt").asString()).isEqualTo("2026-11-27T12:00:00Z");
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void si_el_broker_no_confirma_sale_como_CatedraUnavailableException() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(eq(TOPIC), eq("process-1"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("sin líder")));

        assertThatThrownBy(() -> new KafkaPhoneSubmissionAdapter(kafka, jsonMapper, TOPIC, "g").submit(ENVIO))
                .isInstanceOf(CatedraUnavailableException.class).hasCauseInstanceOf(java.util.concurrent.ExecutionException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void si_send_falla_en_el_acto_tambien_sale_como_CatedraUnavailableException() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(String.class), any(String.class), any(String.class)))
                .thenThrow(new org.apache.kafka.common.errors.TimeoutException("metadata"));

        assertThatThrownBy(() -> new KafkaPhoneSubmissionAdapter(kafka, jsonMapper, TOPIC, "g").submit(ENVIO))
                .isInstanceOf(CatedraUnavailableException.class);
    }

    private ConsumerRecord<String, String> leerUno() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "grupo-lector-" + System.nanoTime(), false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, TOPIC);
            return KafkaTestUtils.getSingleRecord(consumer, TOPIC);
        }
    }
}
