package com.ndonozo.servicioturnosreservas.reservation.infrastructure.web;

import com.ndonozo.servicioturnosreservas.config.GlobalExceptionHandler;
import com.ndonozo.servicioturnosreservas.config.ProblemJsonSecurityHandlers;
import com.ndonozo.servicioturnosreservas.config.SecurityConfig;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.HoldExpiredException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.InvalidPhoneNumberException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.InvalidReservationStateException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.ReservationNotFoundException;
import com.ndonozo.servicioturnosreservas.reservation.application.service.ReservationService;
import com.ndonozo.servicioturnosreservas.reservation.domain.exception.ReservationConflictException;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationProcess;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.ReservationStatus;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.SubmitPhoneCommand;
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

/** Contrato REST de enviar el teléfono hacia KMP: cuerpo, validación, propiedad por usuario, errores y protección con JWT. */
@WebMvcTest(ReservationController.class)
@Import({SecurityConfig.class, ProblemJsonSecurityHandlers.class, ReservationDtoMapper.class,
        ReservationExceptionHandler.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.security.jwt.secret=" + ReservationPhoneControllerTest.SECRETO)
class ReservationPhoneControllerTest {

    // 64 bytes en Base64, solo para pruebas
    static final String SECRETO =
            "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBw==";
    private static final String RUTA = "/api/reservations/process-a92b47d0/phone";
    private static final String CUERPO = "{\"phoneNumber\":\"+54 9 261 555-1234\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService service;

    private ResultActions enviar(String cuerpo) throws Exception {
        return mockMvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static ReservationProcess enviado() {
        return ReservationProcess.builder().id(1L).userId(42).reservationProcessId("process-a92b47d0")
                .holdId("hold-secreto").professionalId(15).date(LocalDate.of(2026, 11, 30)).startTime(LocalTime.of(9, 30))
                .externalPatientId("user-42").status(ReservationStatus.PHONE_SUBMITTED)
                .expiresAt(Instant.parse("2026-11-30T14:44:50Z")).build();
    }

    @Test
    void responde_202_con_el_proceso_y_su_estado_y_sin_el_hold_id() throws Exception {
        when(service.submitPhone(any())).thenReturn(enviado());

        enviar(CUERPO)
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reservationProcessId").value("process-a92b47d0"))
                .andExpect(jsonPath("$.status").value("PHONE_SUBMITTED"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-30T14:44:50Z"))
                .andExpect(jsonPath("$.holdId").doesNotExist())
                .andExpect(content().string(not(containsString("hold-secreto"))));
    }

    @Test
    void el_usuario_sale_del_token_y_el_proceso_de_la_ruta_y_el_telefono_llega_sin_espacios_sobrantes() throws Exception {
        when(service.submitPhone(any())).thenReturn(enviado());

        enviar("{\"phoneNumber\":\"  2615551234 \",\"userId\":999}").andExpect(status().isAccepted());

        verify(service).submitPhone(new SubmitPhoneCommand(42, "process-a92b47d0", "2615551234"));
    }

    @Test
    void el_telefono_es_obligatorio_y_no_llega_a_la_fachada() throws Exception {
        for (String cuerpo : new String[]{"{}", "{\"phoneNumber\":\"  \"}", "{\"phoneNumber\":\"" + "1".repeat(41) + "\"}"}) {
            enviar(cuerpo).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void un_cuerpo_ilegible_responde_400_MALFORMED_REQUEST() throws Exception {
        enviar("esto no es json").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void cada_error_de_negocio_sale_con_su_estado_y_code() throws Exception {
        verificaError(new InvalidPhoneNumberException("teléfono"), 400, "INVALID_PHONE_NUMBER");
        verificaError(new ReservationNotFoundException("No existe la reserva x"), 404, "RESERVATION_NOT_FOUND");
        verificaError(new InvalidReservationStateException("estado"), 409, "INVALID_RESERVATION_STATE");
        verificaError(new HoldExpiredException("venció"), 409, "HOLD_EXPIRED");
        verificaError(new ReservationConflictException("choque", null), 409, "CONCURRENT_UPDATE");
    }

    @Test
    void si_no_se_pudo_publicar_responde_503_generico_sin_filtrar_detalles() throws Exception {
        doThrow(new CatedraUnavailableException("broker interno:9092 falló")).when(service).submitPhone(any());

        enviar(CUERPO).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("CATEDRA_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("interno"))));
    }

    @Test
    void sin_token_responde_401_y_no_llega_a_la_fachada() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    private void verificaError(RuntimeException error, int estado, String code) throws Exception {
        doThrow(error).when(service).submitPhone(any());
        enviar(CUERPO).andExpect(status().is(estado)).andExpect(jsonPath("$.code").value(code));
    }

    private static String token() {
        User ana = User.builder().id(42L).login("ana").authorities(Set.of(User.ROLE_USER)).build();
        return new JwtTokenIssuer(SECRETO, Duration.ofHours(1)).issue(ana).value();
    }
}
