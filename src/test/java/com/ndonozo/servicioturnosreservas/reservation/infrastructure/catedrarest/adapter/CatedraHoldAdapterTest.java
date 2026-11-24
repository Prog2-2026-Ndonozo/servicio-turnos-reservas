package com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.adapter;

import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraIntegrationException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.Hold;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.HoldResult;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.client.CatedraHoldRejectedException;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.client.CatedraHoldRestClient;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.HoldRequest;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.dto.HoldResponse;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.catedrarest.mapper.CatedraHoldMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatedraHoldAdapterTest {

    private static final Instant VENCE = Instant.parse("2026-09-10T14:44:50Z");

    @Mock
    private CatedraHoldRestClient client;

    private CatedraHoldAdapter adapter() {
        return new CatedraHoldAdapter(client, new CatedraHoldMapper());
    }

    @Test
    void envia_la_fecha_ISO_y_la_hora_como_HH_mm_aunque_el_dominio_la_tenga_con_segundos() {
        when(client.createHold(new HoldRequest(15, "2026-09-14", "09:30", "user-42")))
                .thenReturn(new HoldResponse("hold-1", "process-1", "HELD", VENCE));

        HoldResult resultado = adapter().createHold(15, LocalDate.of(2026, 9, 14), LocalTime.of(9, 30, 0), "user-42");

        assertThat(resultado).isEqualTo(new HoldResult.Created(new Hold("hold-1", "process-1", VENCE)));
    }

    @Test
    void cada_code_de_rechazo_se_traduce_a_su_resultado() {
        assertThat(rechazo("SLOT_ALREADY_HELD")).isInstanceOf(HoldResult.SlotAlreadyHeld.class);
        assertThat(rechazo("SLOT_ALREADY_RESERVED")).isInstanceOf(HoldResult.SlotAlreadyReserved.class);
        assertThat(rechazo("INVALID_SLOT")).isInstanceOf(HoldResult.InvalidSlot.class);
        assertThat(rechazo("PROFESSIONAL_DISABLED")).isInstanceOf(HoldResult.ProfessionalDisabled.class);
        assertThat(rechazo("PROFESSIONAL_NOT_FOUND")).isInstanceOf(HoldResult.ProfessionalNotFound.class);
    }

    @Test
    void un_code_que_el_cliente_no_deberia_dejar_pasar_es_de_integracion() {
        when(client.createHold(new HoldRequest(15, "2026-09-14", "09:30", "user-42")))
                .thenThrow(new CatedraHoldRejectedException("OTRO"));

        assertThatThrownBy(() -> adapter().createHold(15, LocalDate.of(2026, 9, 14), LocalTime.of(9, 30), "user-42"))
                .isInstanceOf(CatedraIntegrationException.class);
    }

    @Test
    void las_fallas_de_comunicacion_no_se_atrapan() {
        when(client.createHold(new HoldRequest(15, "2026-09-14", "09:30", "user-42")))
                .thenThrow(new CatedraUnavailableException("sin conexión"));

        assertThatThrownBy(() -> adapter().createHold(15, LocalDate.of(2026, 9, 14), LocalTime.of(9, 30), "user-42"))
                .isInstanceOf(CatedraUnavailableException.class);
    }

    private HoldResult rechazo(String code) {
        doThrow(new CatedraHoldRejectedException(code)).when(client)
                .createHold(new HoldRequest(15, "2026-09-14", "09:30", "user-42"));
        return adapter().createHold(15, LocalDate.of(2026, 9, 14), LocalTime.of(9, 30), "user-42");
    }
}
