package com.ndonozo.servicioturnosreservas.reservation.infrastructure.kafka;

import com.ndonozo.servicioturnosreservas.reservation.application.service.ReservationService;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneRequestResult;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

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
    void otros_tipos_de_evento_se_ignoran_hasta_T9() {
        for (String tipo : new String[]{"AppointmentConfirmed", "AdditionalInformationRejected", "AppointmentProcessExpired",
                "AppointmentProcessInvalid", "AppointmentCancelled", "Desconocido"}) {
            listener().onMessage(PEDIDO.replace("AdditionalInformationRequested", tipo));
        }

        verifyNoInteractions(service);
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
