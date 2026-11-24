package com.ndonozo.servicioturnosreservas.availability.infrastructure.web;

import com.ndonozo.servicioturnosreservas.availability.application.exception.AgendaIntegrationException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.AgendaUnavailableException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.CatedraIntegrationException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.InvalidDateRangeException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.ProfessionalNotFoundException;
import com.ndonozo.servicioturnosreservas.availability.application.service.AvailabilityService;
import com.ndonozo.servicioturnosreservas.availability.domain.model.Availability;
import com.ndonozo.servicioturnosreservas.availability.domain.model.AvailabilitySlot;
import com.ndonozo.servicioturnosreservas.availability.infrastructure.web.controller.AvailabilityController;
import com.ndonozo.servicioturnosreservas.availability.infrastructure.web.exception.AvailabilityExceptionHandler;
import com.ndonozo.servicioturnosreservas.availability.infrastructure.web.mapper.AvailabilityDtoMapper;
import com.ndonozo.servicioturnosreservas.config.GlobalExceptionHandler;
import com.ndonozo.servicioturnosreservas.config.ProblemJsonSecurityHandlers;
import com.ndonozo.servicioturnosreservas.config.SecurityConfig;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato REST de la disponibilidad hacia KMP: parámetros, validación, errores y protección con el JWT del usuario. */
@WebMvcTest(AvailabilityController.class)
@Import({SecurityConfig.class, ProblemJsonSecurityHandlers.class, AvailabilityDtoMapper.class,
        AvailabilityExceptionHandler.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.security.jwt.secret=" + AvailabilityControllerTest.SECRETO)
class AvailabilityControllerTest {

    // 64 bytes en Base64, solo para pruebas
    static final String SECRETO =
            "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBw==";
    private static final String RUTA = "/api/availability";
    private static final LocalDate DESDE = LocalDate.of(2026, 11, 23);
    private static final LocalDate HASTA = LocalDate.of(2026, 11, 29);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AvailabilityService service;

    private ResultActions pedir(String profesional, String desde, String hasta) throws Exception {
        var pedido = get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario());
        if (profesional != null) pedido.queryParam("professionalId", profesional);
        if (desde != null) pedido.queryParam("from", desde);
        if (hasta != null) pedido.queryParam("to", hasta);
        return mockMvc.perform(pedido);
    }

    private ResultActions pedirValido() throws Exception {
        return pedir("15", "2026-11-23", "2026-11-29");
    }

    @Test
    void responde_200_con_los_turnos_en_el_orden_recibido() throws Exception {
        when(service.getAvailability(15, DESDE, HASTA)).thenReturn(new Availability(15, DESDE, HASTA, List.of(
                new AvailabilitySlot(DESDE, LocalTime.of(9, 0), LocalTime.of(9, 30)),
                new AvailabilitySlot(DESDE, LocalTime.of(9, 30), LocalTime.of(10, 0)))));

        pedirValido()
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.professionalId").value(15))
                .andExpect(jsonPath("$.from").value("2026-11-23"))
                .andExpect(jsonPath("$.to").value("2026-11-29"))
                .andExpect(jsonPath("$.slots.length()").value(2))
                .andExpect(jsonPath("$.slots[0].date").value("2026-11-23"))
                .andExpect(jsonPath("$.slots[0].startTime").value("09:00:00"))
                .andExpect(jsonPath("$.slots[0].endTime").value("09:30:00"))
                .andExpect(jsonPath("$.slots[1].startTime").value("09:30:00"));
        verify(service).getAvailability(15, DESDE, HASTA);
    }

    @Test
    void sin_turnos_la_lista_va_vacia() throws Exception {
        when(service.getAvailability(15, DESDE, HASTA)).thenReturn(new Availability(15, DESDE, HASTA, List.of()));

        pedirValido().andExpect(status().isOk()).andExpect(jsonPath("$.slots.length()").value(0));
    }

    // ---------- validación: un pedido inválido no llega a la fachada ----------

    @Test
    void los_tres_parametros_son_obligatorios() throws Exception {
        pedir(null, "2026-11-23", "2026-11-29").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("professionalId"));
        pedir("15", null, "2026-11-29").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
        pedir("15", "2026-11-23", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"));
        verifyNoInteractions(service);
    }

    @Test
    void un_profesional_no_positivo_o_no_numerico_es_invalido() throws Exception {
        pedir("0", "2026-11-23", "2026-11-29").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("professionalId"));
        pedir("-3", "2026-11-23", "2026-11-29").andExpect(status().isBadRequest());
        pedir("abc", "2026-11-23", "2026-11-29").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("tiene un formato inválido"))
                .andExpect(content().string(not(containsString("java.lang"))));
        verifyNoInteractions(service);
    }

    @Test
    void las_fechas_con_otro_formato_son_invalidas() throws Exception {
        pedir("15", "23/11/2026", "2026-11-29").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("from"))
                .andExpect(jsonPath("$.errors[0].message").value("tiene un formato inválido"));
        pedir("15", "2026-11-23", "2026-13-40").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"));
        verifyNoInteractions(service);
    }

    // ---------- errores ----------

    @Test
    void un_rango_invalido_responde_400_INVALID_DATE_RANGE() throws Exception {
        doThrow(new InvalidDateRangeException("rango mal")).when(service).getAvailability(anyLong(), any(), any());

        pedirValido().andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void un_profesional_inexistente_responde_404() throws Exception {
        doThrow(new ProfessionalNotFoundException(15)).when(service).getAvailability(anyLong(), any(), any());

        pedirValido().andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFESSIONAL_NOT_FOUND"));
    }

    @Test
    void el_catalogo_caido_responde_503_y_el_de_integracion_502_sin_detalles_internos() throws Exception {
        doThrow(new AgendaUnavailableException("http://interno:8080/token-xyz")).when(service).getAvailability(anyLong(), any(), any());
        pedirValido().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("interno"))));

        doThrow(new AgendaIntegrationException("403 token-xyz")).when(service).getAvailability(anyLong(), any(), any());
        pedirValido().andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CATALOG_ERROR"))
                .andExpect(content().string(not(containsString("token-xyz"))));
    }

    @Test
    void la_catedra_caida_responde_503_y_la_de_integracion_502_sin_detalles_internos() throws Exception {
        doThrow(new CatedraUnavailableException("https://catedra.interna/jwt-abc")).when(service).getAvailability(anyLong(), any(), any());
        pedirValido().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATEDRA_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("catedra.interna"))));

        doThrow(new CatedraIntegrationException("401 jwt-abc")).when(service).getAvailability(anyLong(), any(), any());
        pedirValido().andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CATEDRA_ERROR"))
                .andExpect(content().string(not(containsString("jwt-abc"))));
    }

    // ---------- protección ----------

    @Test
    void sin_token_responde_401_y_la_fachada_no_se_toca() throws Exception {
        mockMvc.perform(get(RUTA).queryParam("professionalId", "15").queryParam("from", "2026-11-23")
                        .queryParam("to", "2026-11-29"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(service);
    }

    @Test
    void un_token_invalido_responde_401() throws Exception {
        mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer basura")
                        .queryParam("professionalId", "15").queryParam("from", "2026-11-23").queryParam("to", "2026-11-29"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    private static String tokenDeUsuario() {
        User ana = User.builder().id(42L).login("ana").authorities(Set.of(User.ROLE_USER)).build();
        return new JwtTokenIssuer(SECRETO, Duration.ofHours(1)).issue(ana).value();
    }
}
