package com.ndonozo.servicioturnosreservas.reservation.infrastructure.web;

import com.ndonozo.servicioturnosreservas.config.GlobalExceptionHandler;
import com.ndonozo.servicioturnosreservas.config.ProblemJsonSecurityHandlers;
import com.ndonozo.servicioturnosreservas.config.SecurityConfig;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraIntegrationException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.HoldExpiredException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.InvalidReservationStateException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.ReservationNotFoundException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.SlotAlreadyReservedException;
import com.ndonozo.servicioturnosreservas.reservation.application.service.ReservationService;
import com.ndonozo.servicioturnosreservas.reservation.domain.exception.ReservationConflictException;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ConfirmReservationCommand;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationStatus;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.controller.ReservationController;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.exception.ReservationExceptionHandler;
import com.ndonozo.servicioturnosreservas.reservation.infrastructure.web.mapper.ReservationDtoMapper;
import com.ndonozo.servicioturnosreservas.user.domain.model.User;
import com.ndonozo.servicioturnosreservas.user.infrastructure.security.JwtTokenIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato REST de confirmar el hold hacia KMP: cuerpo, validación, propiedad por usuario, errores y protección con JWT. */
@WebMvcTest(ReservationController.class)
@Import({SecurityConfig.class, ProblemJsonSecurityHandlers.class, ReservationDtoMapper.class,
        ReservationExceptionHandler.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.security.jwt.secret=" + ReservationConfirmControllerTest.SECRETO)
class ReservationConfirmControllerTest {

    // 64 bytes en Base64, solo para pruebas
    static final String SECRETO =
            "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBw==";
    private static final String RUTA = "/api/reservations/process-a92b47d0/confirm";
    private static final String CUERPO = "{\"firstName\":\"Juan\",\"lastName\":\"Perez\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService service;

    private ResultActions confirmar(String cuerpo) throws Exception {
        return mockMvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static ReservationProcess esperandoTelefono() {
        return ReservationProcess.builder().id(1L).userId(42).reservationProcessId("process-a92b47d0")
                .holdId("hold-secreto").professionalId(15).date(LocalDate.of(2026, 11, 30)).startTime(LocalTime.of(9, 30))
                .externalPatientId("user-42").status(ReservationStatus.WAITING_FOR_PHONE)
                .expiresAt(Instant.parse("2026-11-30T14:44:50Z")).build();
    }

    @Test
    void responde_202_con_el_proceso_y_su_estado_y_sin_el_hold_id() throws Exception {
        when(service.confirmReservation(any())).thenReturn(esperandoTelefono());

        confirmar(CUERPO)
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reservationProcessId").value("process-a92b47d0"))
                .andExpect(jsonPath("$.status").value("WAITING_FOR_PHONE"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-30T14:44:50Z"))
                .andExpect(jsonPath("$.holdId").doesNotExist())
                .andExpect(content().string(not(containsString("hold-secreto"))));
    }

    @Test
    void el_usuario_sale_del_token_el_proceso_de_la_ruta_y_los_nombres_del_cuerpo_sin_espacios_sobrantes() throws Exception {
        when(service.confirmReservation(any())).thenReturn(esperandoTelefono());

        confirmar("{\"firstName\":\"  Juan \",\"lastName\":\" Perez  \",\"userId\":999}").andExpect(status().isAccepted());

        verify(service).confirmReservation(new ConfirmReservationCommand(42, "process-a92b47d0", "Juan", "Perez"));
    }

    @Test
    void nombre_y_apellido_son_obligatorios_de_2_a_100_caracteres_y_no_llegan_a_la_fachada() throws Exception {
        for (String cuerpo : new String[]{"{\"lastName\":\"Perez\"}", "{\"firstName\":\"Juan\"}", "{}",
                "{\"firstName\":\"  \",\"lastName\":\"Perez\"}", "{\"firstName\":\"J\",\"lastName\":\"Perez\"}",
                "{\"firstName\":\"Juan\",\"lastName\":\"" + "p".repeat(101) + "\"}"}) {
            confirmar(cuerpo).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void un_cuerpo_ilegible_responde_400_MALFORMED_REQUEST() throws Exception {
        confirmar("esto no es json").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void cada_error_de_negocio_sale_con_su_estado_y_code() throws Exception {
        verificaError(new ReservationNotFoundException("No existe la reserva x"), 404, "RESERVATION_NOT_FOUND");
        verificaError(new InvalidReservationStateException("estado"), 409, "INVALID_RESERVATION_STATE");
        verificaError(new HoldExpiredException("venció"), 409, "HOLD_EXPIRED");
        verificaError(new SlotAlreadyReservedException("reservado"), 409, "SLOT_ALREADY_RESERVED");
        verificaError(new ReservationConflictException("choque", null), 409, "CONCURRENT_UPDATE");
    }

    @Test
    void los_errores_de_la_catedra_salen_genericos_sin_filtrar_detalles() throws Exception {
        doThrow(new CatedraUnavailableException("http://interna:8080 token=abc")).when(service).confirmReservation(any());
        confirmar(CUERPO).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("CATEDRA_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("interna"))));

        doThrow(new CatedraIntegrationException("HTTP 403 (HOLD_OWNER_MISMATCH) jwt-secreto")).when(service).confirmReservation(any());
        confirmar(CUERPO).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("CATEDRA_ERROR"))
                .andExpect(content().string(not(containsString("jwt-secreto"))));
    }

    @Test
    void sin_token_responde_401_y_no_llega_a_la_fachada() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    private void verificaError(RuntimeException error, int estado, String code) throws Exception {
        doThrow(error).when(service).confirmReservation(any());
        confirmar(CUERPO).andExpect(status().is(estado)).andExpect(jsonPath("$.code").value(code));
    }

    private static String token() {
        User ana = User.builder().id(42L).login("ana").authorities(Set.of(User.ROLE_USER)).build();
        return new JwtTokenIssuer(SECRETO, Duration.ofHours(1)).issue(ana).value();
    }
}
