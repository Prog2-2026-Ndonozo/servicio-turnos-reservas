package com.ndonozo.servicioturnosreservas.reservation.infrastructure.web;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.ndonozo.servicioturnosreservas.config.GlobalExceptionHandler;
import com.ndonozo.servicioturnosreservas.config.ProblemJsonSecurityHandlers;
import com.ndonozo.servicioturnosreservas.config.SecurityConfig;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.AgendaIntegrationException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.AgendaUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraIntegrationException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.InvalidSlotException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.ProfessionalDisabledException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.ProfessionalNotFoundException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.SlotAlreadyHeldException;
import com.ndonozo.servicioturnosreservas.reservation.application.exception.SlotAlreadyReservedException;
import com.ndonozo.servicioturnosreservas.reservation.application.service.ReservationService;
import com.ndonozo.servicioturnosreservas.reservation.domain.model.CreateReservationCommand;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato REST de crear la reserva hacia KMP: cuerpo, validación, errores y protección con el JWT del usuario real. */
@WebMvcTest(ReservationController.class)
@Import({SecurityConfig.class, ProblemJsonSecurityHandlers.class, ReservationDtoMapper.class,
        ReservationExceptionHandler.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.security.jwt.secret=" + ReservationControllerTest.SECRETO)
class ReservationControllerTest {

    // 64 bytes en Base64, solo para pruebas
    static final String SECRETO =
            "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBw==";
    private static final String RUTA = "/api/reservations";
    private static final String CUERPO = "{\"professionalId\":15,\"date\":\"2026-11-30\",\"startTime\":\"09:30\"}";
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 30);
    private static final Instant VENCE = Instant.parse("2026-11-30T14:44:50Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService service;

    private ResultActions crear(String cuerpo) throws Exception {
        return mockMvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static ReservationProcess proceso() {
        return ReservationProcess.builder().id(1L).userId(42).reservationProcessId("process-a92b47d0")
                .holdId("hold-secreto").professionalId(15).date(FECHA).startTime(LocalTime.of(9, 30))
                .externalPatientId("user-42").status(ReservationStatus.HELD).expiresAt(VENCE).build();
    }

    // ---------- éxito ----------

    @Test
    void responde_201_con_el_proceso_el_estado_y_el_vencimiento_y_sin_el_hold_id() throws Exception {
        when(service.createReservation(any())).thenReturn(proceso());

        crear(CUERPO)
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reservationProcessId").value("process-a92b47d0"))
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-30T14:44:50Z"))
                .andExpect(jsonPath("$.holdId").doesNotExist())
                .andExpect(content().string(not(containsString("hold-secreto"))));
    }

    @Test
    void el_usuario_sale_del_claim_uid_del_token_y_no_del_cuerpo() throws Exception {
        when(service.createReservation(any())).thenReturn(proceso());

        crear("{\"professionalId\":15,\"date\":\"2026-11-30\",\"startTime\":\"09:30\",\"userId\":999,\"uid\":999}")
                .andExpect(status().isCreated());

        verify(service).createReservation(new CreateReservationCommand(42, 15, FECHA, LocalTime.of(9, 30)));
    }

    @Test
    void la_hora_se_acepta_con_o_sin_segundos() throws Exception {
        when(service.createReservation(any())).thenReturn(proceso());

        crear("{\"professionalId\":15,\"date\":\"2026-11-30\",\"startTime\":\"09:30:00\"}").andExpect(status().isCreated());

        verify(service).createReservation(new CreateReservationCommand(42, 15, FECHA, LocalTime.of(9, 30)));
    }

    // ---------- validación: un pedido inválido no llega a la fachada ----------

    @Test
    void los_tres_campos_son_obligatorios() throws Exception {
        crear("{\"date\":\"2026-11-30\",\"startTime\":\"09:30\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("professionalId"));
        crear("{\"professionalId\":15,\"startTime\":\"09:30\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("date"));
        crear("{\"professionalId\":15,\"date\":\"2026-11-30\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("startTime"));
        verifyNoInteractions(service);
    }

    @Test
    void el_profesional_tiene_que_ser_positivo() throws Exception {
        crear("{\"professionalId\":0,\"date\":\"2026-11-30\",\"startTime\":\"09:30\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("professionalId"));
        crear("{\"professionalId\":-3,\"date\":\"2026-11-30\",\"startTime\":\"09:30\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void una_fecha_o_una_hora_mal_formadas_o_un_cuerpo_roto_son_400_MALFORMED_REQUEST() throws Exception {
        crear("{\"professionalId\":15,\"date\":\"30/11/2026\",\"startTime\":\"09:30\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        crear("{\"professionalId\":15,\"date\":\"2026-11-30\",\"startTime\":\"25:99\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        crear("{esto no es json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario())
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    // ---------- errores de negocio ----------

    @Test
    void cada_error_de_negocio_responde_su_estado_y_su_code() throws Exception {
        verificaError(new InvalidSlotException("x"), 400, "INVALID_SLOT");
        verificaError(new ProfessionalDisabledException("x"), 400, "PROFESSIONAL_DISABLED");
        verificaError(new ProfessionalNotFoundException(15), 404, "PROFESSIONAL_NOT_FOUND");
        verificaError(new SlotAlreadyHeldException("x"), 409, "SLOT_ALREADY_HELD");
        verificaError(new SlotAlreadyReservedException("x"), 409, "SLOT_ALREADY_RESERVED");
    }

    @Test
    void el_catalogo_caido_responde_503_y_el_de_integracion_502_sin_detalles_internos() throws Exception {
        doThrow(new AgendaUnavailableException("http://interno:8080/token-xyz")).when(service).createReservation(any());
        crear(CUERPO).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("interno"))));

        doThrow(new AgendaIntegrationException("403 token-xyz")).when(service).createReservation(any());
        crear(CUERPO).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CATALOG_ERROR"))
                .andExpect(content().string(not(containsString("token-xyz"))));
    }

    @Test
    void la_catedra_caida_responde_503_y_la_de_integracion_502_sin_detalles_internos() throws Exception {
        doThrow(new CatedraUnavailableException("https://catedra.interna/jwt-abc")).when(service).createReservation(any());
        crear(CUERPO).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATEDRA_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("catedra.interna"))));

        doThrow(new CatedraIntegrationException("401 jwt-abc")).when(service).createReservation(any());
        crear(CUERPO).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CATEDRA_ERROR"))
                .andExpect(content().string(not(containsString("jwt-abc"))));
    }

    private void verificaError(RuntimeException error, int estado, String code) throws Exception {
        doThrow(error).when(service).createReservation(any());
        crear(CUERPO).andExpect(status().is(estado))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code));
    }

    // ---------- protección ----------

    @Test
    void sin_token_responde_401_y_la_fachada_no_se_toca() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(service);
    }

    @Test
    void un_token_invalido_responde_401() throws Exception {
        mockMvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer basura")
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void un_token_valido_sin_uid_no_sirve_para_reservar() throws Exception {
        mockMvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenSinUid())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(service);
    }

    private static String tokenDeUsuario() {
        User ana = User.builder().id(42L).login("ana").authorities(Set.of(User.ROLE_USER)).build();
        return new JwtTokenIssuer(SECRETO, Duration.ofHours(1)).issue(ana).value();
    }

    // Firmado con el mismo secreto y algoritmo que los del servicio, pero sin el claim uid.
    private static String tokenSinUid() {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(new SecretKeySpec(Base64.getDecoder().decode(SECRETO), "HmacSHA512")));
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("ana").issuedAt(ahora).expiresAt(ahora.plusSeconds(3600))
                .claim("auth", "ROLE_USER").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS512).build(), claims)).getTokenValue();
    }
}
