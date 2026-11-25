package com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.adapter;

import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraIntegrationException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ConfirmHoldResult;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.client.CatedraHoldRejectedException;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.client.CatedraHoldRestClient;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.ConfirmHoldRequest;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.ConfirmHoldResponse;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.mapper.CatedraHoldMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatedraHoldAdapterConfirmTest {

    private static final ConfirmHoldRequest PEDIDO = new ConfirmHoldRequest("user-42", "Juan", "Perez");

    @Mock
    private CatedraHoldRestClient client;

    private CatedraHoldAdapter adapter() {
        return new CatedraHoldAdapter(client, new CatedraHoldMapper());
    }

    @Test
    void arma_el_pedido_con_los_nombres_del_contrato_y_devuelve_Confirmed() {
        when(client.confirmHold("hold-1", PEDIDO))
                .thenReturn(new ConfirmHoldResponse("process-1", "WAITING_FOR_PHONE", Instant.parse("2026-09-10T14:44:50Z")));

        assertThat(adapter().confirmHold("hold-1", "user-42", "Juan", "Perez")).isInstanceOf(ConfirmHoldResult.Confirmed.class);
    }

    @Test
    void cada_code_de_rechazo_se_traduce_a_su_resultado() {
        assertThat(rechazo("HOLD_EXPIRED")).isInstanceOf(ConfirmHoldResult.HoldExpired.class);
        assertThat(rechazo("HOLD_NOT_FOUND")).isInstanceOf(ConfirmHoldResult.HoldExpired.class);
        assertThat(rechazo("RESERVATION_PROCESS_ALREADY_CONFIRMED")).isInstanceOf(ConfirmHoldResult.AlreadyConfirmed.class);
        assertThat(rechazo("SLOT_ALREADY_RESERVED")).isInstanceOf(ConfirmHoldResult.SlotAlreadyReserved.class);
    }

    @Test
    void un_code_que_el_cliente_no_deberia_dejar_pasar_es_de_integracion() {
        when(client.confirmHold("hold-1", PEDIDO)).thenThrow(new CatedraHoldRejectedException("OTRO"));

        assertThatThrownBy(() -> adapter().confirmHold("hold-1", "user-42", "Juan", "Perez"))
                .isInstanceOf(CatedraIntegrationException.class);
    }

    @Test
    void las_fallas_de_comunicacion_no_se_atrapan() {
        when(client.confirmHold("hold-1", PEDIDO)).thenThrow(new CatedraUnavailableException("sin conexión"));

        assertThatThrownBy(() -> adapter().confirmHold("hold-1", "user-42", "Juan", "Perez"))
                .isInstanceOf(CatedraUnavailableException.class);
    }

    private ConfirmHoldResult rechazo(String code) {
        doThrow(new CatedraHoldRejectedException(code)).when(client).confirmHold("hold-1", PEDIDO);
        return adapter().confirmHold("hold-1", "user-42", "Juan", "Perez");
    }
}
