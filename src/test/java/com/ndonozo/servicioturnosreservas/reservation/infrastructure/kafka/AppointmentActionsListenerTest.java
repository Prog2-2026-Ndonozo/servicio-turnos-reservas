package com.ndonozo.servicioturnosreservas.reservation.infrastructure.kafka;

import com.ndonozo.servicioturnosreservas.reservation.application.service.ReservationService;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.AppointmentResultEvent;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.AppointmentResultOutcome;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneRequestResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneRequestedEvent;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentActionsListenerTest {

    private static final String PEDIDO = """
            {"eventId":"request-event-8a31","eventType":"AdditionalInformationRequested","reservationProcessId":"process-a92b47d0",
             "holdId":"hold-6c0c2f41","requiredField":"PHONE_NUMBER","message":"Ingrese un numero de telefono para continuar.",
             "expiresAt":"2026-09-10T14:44:50Z","occurredAt":"2026-09-10T14:35:10Z","schemaVersion":1,"campoNuevo":true}""";

    @Mock
    private ReservationService service;

    private AppointmentActionsListener listener() {
        return new AppointmentActionsListener(JsonMapper.builder().build(), service);
    }

    @Test
    void un_pedido_de_telefono_valido_llega_a_la_fachada_con_sus_tres_datos_e_ignora_campos_desconocidos() {
        PhoneRequestedEvent esperado = new PhoneRequestedEvent("request-event-8a31", "process-a92b47d0", "hold-6c0c2f41");
        when(service.handlePhoneRequested(esperado)).thenReturn(new PhoneRequestResult.Duplicate());

        listener().onMessage(PEDIDO);

        verify(service).handlePhoneRequested(esperado);
    }

    @Test
    void cada_resultado_se_registra_sin_fallar() {
        when(service.handlePhoneRequested(any())).thenReturn(new PhoneRequestResult.ProcessNotFound(),
                new PhoneRequestResult.HoldMismatch(), new PhoneRequestResult.NotApplicable(),
                new PhoneRequestResult.Duplicate());

        for (int i = 0; i < 4; i++) {
            listener().onMessage(PEDIDO);
        }
    }

    @Test
    void lo_ilegible_o_vacio_se_descarta_sin_llegar_a_la_fachada() {
        listener().onMessage("esto no es json");
        listener().onMessage("");
        listener().onMessage("null");

        verifyNoInteractions(service);
    }

    @Test
    void la_cancelacion_y_los_tipos_desconocidos_se_ignoran() {
        for (String tipo : new String[]{"AppointmentCancelled", "Desconocido"}) {
            listener().onMessage(PEDIDO.replace("AdditionalInformationRequested", tipo));
        }
        listener().onMessage("{\"eventId\":\"x\"}");

        verifyNoInteractions(service);
    }

    @Test
    void la_confirmacion_llega_a_la_fachada_con_el_reservationId() {
        AppointmentResultEvent esperado = new AppointmentResultEvent.Confirmed("confirmation-event-7d10", "process-a92b47d0", "hold-6c0c2f41", 8251L);
        when(service.handleAppointmentResult(esperado)).thenReturn(new AppointmentResultOutcome.Duplicate());

        listener().onMessage(resultado("AppointmentConfirmed", "confirmation-event-7d10", "\"reservationId\":8251,\"status\":\"CONFIRMED\""));

        verify(service).handleAppointmentResult(esperado);
    }

    @Test
    void el_rechazo_llega_con_el_motivo_y_el_vencimiento_nuevo() {
        AppointmentResultEvent esperado = new AppointmentResultEvent.Rejected("rejection-event-7d11", "process-a92b47d0",
                "hold-6c0c2f41", "INVALID_PHONE_NUMBER", Instant.parse("2026-09-10T14:44:50Z"));
        when(service.handleAppointmentResult(esperado)).thenReturn(new AppointmentResultOutcome.Duplicate());

        listener().onMessage(resultado("AdditionalInformationRejected", "rejection-event-7d11",
                "\"reason\":\"INVALID_PHONE_NUMBER\",\"message\":\"El telefono ingresado no es valido.\",\"expiresAt\":\"2026-09-10T14:44:50Z\""));

        verify(service).handleAppointmentResult(esperado);
    }

    @Test
    void un_rechazo_con_vencimiento_ilegible_o_sin_motivo_igual_se_aplica() {
        when(service.handleAppointmentResult(any())).thenReturn(new AppointmentResultOutcome.Duplicate());

        listener().onMessage(resultado("AdditionalInformationRejected", "rejection-event-1", "\"expiresAt\":\"mañana\""));

        verify(service).handleAppointmentResult(
                new AppointmentResultEvent.Rejected("rejection-event-1", "process-a92b47d0", "hold-6c0c2f41", null, null));
    }

    @Test
    void un_motivo_demasiado_largo_se_recorta_a_100_caracteres() {
        when(service.handleAppointmentResult(any())).thenReturn(new AppointmentResultOutcome.Duplicate());

        listener().onMessage(resultado("AdditionalInformationRejected", "rejection-event-1", "\"reason\":\"" + "m".repeat(300) + "\""));

        verify(service).handleAppointmentResult(new AppointmentResultEvent.Rejected("rejection-event-1", "process-a92b47d0",
                "hold-6c0c2f41", "m".repeat(100), null));
    }

    @Test
    void la_expiracion_y_el_proceso_invalido_llegan_a_la_fachada() {
        when(service.handleAppointmentResult(any())).thenReturn(new AppointmentResultOutcome.Duplicate());

        listener().onMessage(resultado("AppointmentProcessExpired", "process-event-7d12", "\"reason\":\"HOLD_EXPIRED\""));
        listener().onMessage(resultado("AppointmentProcessInvalid", "process-event-7d13", "\"reason\":\"REQUEST_EVENT_MISMATCH\""));

        verify(service).handleAppointmentResult(new AppointmentResultEvent.Expired("process-event-7d12", "process-a92b47d0", "hold-6c0c2f41"));
        verify(service).handleAppointmentResult(new AppointmentResultEvent.Invalid("process-event-7d13", "process-a92b47d0", "hold-6c0c2f41"));
    }

    @Test
    void cada_desenlace_de_un_resultado_se_registra_sin_fallar() {
        when(service.handleAppointmentResult(any())).thenReturn(new AppointmentResultOutcome.Applied(ReservationProcess.builder().status(ReservationStatus.EXPIRED).build()),
                new AppointmentResultOutcome.Duplicate(), new AppointmentResultOutcome.ProcessNotFound(),
                new AppointmentResultOutcome.HoldMismatch(), new AppointmentResultOutcome.NotApplicable());

        for (int i = 0; i < 5; i++) {
            listener().onMessage(resultado("AppointmentProcessExpired", "process-event-" + i, "\"reason\":\"HOLD_EXPIRED\""));
        }
    }

    @Test
    void un_resultado_incompleto_se_descarta() {
        // la confirmación sin reservationId, y cualquier resultado sin eventId, proceso o holdId
        listener().onMessage(resultado("AppointmentConfirmed", "confirmation-event-1", "\"status\":\"CONFIRMED\""));
        listener().onMessage(resultado("AppointmentProcessExpired", "process-event-1", "\"reason\":\"HOLD_EXPIRED\"")
                .replace("\"holdId\":\"hold-6c0c2f41\",", ""));
        listener().onMessage(resultado("AppointmentProcessExpired", "process-event-1", "\"reason\":\"HOLD_EXPIRED\"")
                .replace("\"reservationProcessId\":\"process-a92b47d0\"", "\"reservationProcessId\":\" \""));
        listener().onMessage(resultado("AppointmentProcessExpired", " ", "\"reason\":\"HOLD_EXPIRED\""));

        verifyNoInteractions(service);
    }

    @Test
    void un_fallo_operativo_al_aplicar_un_resultado_tambien_se_deja_propagar() {
        when(service.handleAppointmentResult(any())).thenThrow(new IllegalStateException("base caída"));

        assertThatThrownBy(() -> listener().onMessage(resultado("AppointmentProcessExpired", "process-event-1", "\"reason\":\"HOLD_EXPIRED\"")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String resultado(String tipo, String eventId, String campos) {
        return """
                {"eventId":"%s","eventType":"%s","groupId":"proyecto-juan-perez","reservationProcessId":"process-a92b47d0",
                 "holdId":"hold-6c0c2f41","requestEventId":"request-event-8a31",%s,"occurredAt":"2026-09-10T14:36:01Z","schemaVersion":1}"""
                .formatted(eventId, tipo, campos);
    }

    @Test
    void un_pedido_incompleto_o_que_no_pide_un_telefono_se_descarta() {
        listener().onMessage(PEDIDO.replace("PHONE_NUMBER", "EMAIL"));
        listener().onMessage(PEDIDO.replace("\"eventId\":\"request-event-8a31\",", ""));
        listener().onMessage(PEDIDO.replace("\"holdId\":\"hold-6c0c2f41\",", ""));
        listener().onMessage(PEDIDO.replace("\"reservationProcessId\":\"process-a92b47d0\"", "\"reservationProcessId\":\" \""));

        verifyNoInteractions(service);
    }

    @Test
    void un_fallo_operativo_se_deja_propagar_para_que_Spring_Kafka_reintente() {
        when(service.handlePhoneRequested(any())).thenThrow(new IllegalStateException("base caída"));

        assertThatThrownBy(() -> listener().onMessage(PEDIDO)).isInstanceOf(IllegalStateException.class);
    }
}
