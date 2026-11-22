package com.ndonozo.servicioturnosreservas.user.infrastructure.web;

import com.ndonozo.servicioturnosreservas.config.GlobalExceptionHandler;
import com.ndonozo.servicioturnosreservas.config.ProblemJsonSecurityHandlers;
import com.ndonozo.servicioturnosreservas.config.SecurityConfig;
import com.ndonozo.servicioturnosreservas.user.application.exception.EmailAlreadyUsedException;
import com.ndonozo.servicioturnosreservas.user.application.exception.InvalidCredentialsException;
import com.ndonozo.servicioturnosreservas.user.application.exception.LoginAlreadyUsedException;
import com.ndonozo.servicioturnosreservas.user.application.service.UserService;
import com.ndonozo.servicioturnosreservas.user.domain.model.AuthToken;
import com.ndonozo.servicioturnosreservas.user.domain.model.RegisterUserCommand;
import com.ndonozo.servicioturnosreservas.user.domain.model.User;
import com.ndonozo.servicioturnosreservas.user.infrastructure.web.controller.UserController;
import com.ndonozo.servicioturnosreservas.user.infrastructure.web.dto.AuthenticateRequest;
import com.ndonozo.servicioturnosreservas.user.infrastructure.web.dto.RegisterRequest;
import com.ndonozo.servicioturnosreservas.user.infrastructure.web.exception.UserExceptionHandler;
import com.ndonozo.servicioturnosreservas.user.infrastructure.web.mapper.UserDtoMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, ProblemJsonSecurityHandlers.class, UserDtoMapper.class,
        UserExceptionHandler.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.security.jwt.secret=BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBw==")
class UserControllerTest {

    private static final String REGISTRO_VALIDO = """
            {"login":"ana","password":"clave1234","firstName":"Ana","lastName":"Paz",
             "email":"ana@correo.com","imageUrl":"http://img/ana.png","langKey":"es"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    // ---------- registro ----------

    @Test
    void registro_valido_responde_201_sin_cuerpo_y_pasa_los_datos_al_servicio() throws Exception {
        when(userService.register(any())).thenReturn(User.builder().id(1L).build());

        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(REGISTRO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        ArgumentCaptor<RegisterUserCommand> captor = ArgumentCaptor.forClass(RegisterUserCommand.class);
        verify(userService).register(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new RegisterUserCommand(
                "ana", "clave1234", "Ana", "Paz", "ana@correo.com", "http://img/ana.png", "es"));
    }

    @Test
    void registro_solo_con_los_campos_obligatorios_es_valido() throws Exception {
        when(userService.register(any())).thenReturn(User.builder().id(1L).build());

        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"login":"ana","password":"clave1234","email":"ana@correo.com","langKey":"es"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    void registro_con_datos_invalidos_responde_400_con_los_campos_y_sin_llamar_al_servicio() throws Exception {
        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"login":"","password":"abc","email":"no-es-un-email","langKey":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItems(
                        "login", "password", "email", "langKey")));

        verifyNoInteractions(userService);
    }

    @Test
    void la_respuesta_de_validacion_no_repite_el_valor_enviado() throws Exception {
        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"login":"ana","password":"abc","email":"ana@correo.com","langKey":"es"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("abc\""))));
    }

    @Test
    void registro_rechaza_cada_limite_del_ADR() throws Exception {
        String[] invalidos = {
                json("login", "a".repeat(51)),
                json("login", "con espacios"),
                json("password", "abc"),
                json("password", "a".repeat(73)),
                json("password", "ñ".repeat(37)), // 74 bytes en UTF-8
                json("firstName", "a".repeat(51)),
                json("lastName", "a".repeat(51)),
                json("email", "a@b"),
                json("email", "a".repeat(250) + "@x.com"),
                json("imageUrl", "a".repeat(257)),
                json("langKey", "e"),
                json("langKey", "a".repeat(11)),
        };
        for (String cuerpo : invalidos) {
            mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(userService);
    }

    @Test
    void registro_acepta_los_limites_exactos_del_ADR() throws Exception {
        when(userService.register(any())).thenReturn(User.builder().id(1L).build());
        String[] validos = {
                json("login", "a".repeat(50)),
                json("password", "abcd"),
                json("password", "a".repeat(72)),
                json("password", "ñ".repeat(36)), // 72 bytes en UTF-8
                json("firstName", "a".repeat(50)),
                json("email", "a@b.c"),
                json("langKey", "es"),
                json("langKey", "a".repeat(10)),
        };
        for (String cuerpo : validos) {
            mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void registro_con_json_mal_formado_responde_400_MALFORMED_REQUEST() throws Exception {
        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void login_repetido_responde_409_LOGIN_ALREADY_USED() throws Exception {
        when(userService.register(any())).thenThrow(new LoginAlreadyUsedException("El login ya está en uso"));

        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(REGISTRO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("LOGIN_ALREADY_USED"));
    }

    @Test
    void email_repetido_responde_409_EMAIL_ALREADY_USED() throws Exception {
        when(userService.register(any())).thenThrow(new EmailAlreadyUsedException("El email ya está en uso"));

        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(REGISTRO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    // ---------- login ----------

    @Test
    void login_valido_responde_200_con_id_token() throws Exception {
        when(userService.authenticate("ana", "clave1234")).thenReturn(new AuthToken("el.jwt.firmado"));

        mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"password\":\"clave1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_token").value("el.jwt.firmado"));
    }

    @Test
    void login_ignora_el_rememberMe_de_JHipster() throws Exception {
        when(userService.authenticate(anyString(), anyString())).thenReturn(new AuthToken("t"));

        mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"password\":\"clave1234\",\"rememberMe\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void login_con_credenciales_invalidas_responde_401_BAD_CREDENTIALS() throws Exception {
        when(userService.authenticate(anyString(), anyString()))
                .thenThrow(new InvalidCredentialsException("Credenciales inválidas"));

        mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void login_con_una_clave_de_mas_de_72_bytes_responde_400_y_no_llega_al_servicio() throws Exception {
        mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"password\":\"" + "a".repeat(73) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(userService);
    }

    @Test
    void login_sin_usuario_o_sin_clave_responde_400() throws Exception {
        mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(userService);
    }

    // ---------- seguridad ----------

    @Test
    void register_y_authenticate_son_publicos_y_no_piden_csrf() throws Exception {
        when(userService.register(any())).thenReturn(User.builder().id(1L).build());

        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(REGISTRO_VALIDO))
                .andExpect(status().isCreated());
    }

    @Test
    void cualquier_otra_ruta_exige_autenticacion() throws Exception {
        mockMvc.perform(get("/api/lo-que-sea")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/register")).andExpect(status().isUnauthorized());
    }

    // ---------- que la contraseña no se filtre ----------

    @Test
    void los_toString_de_los_pedidos_no_incluyen_la_contrasena() {
        RegisterRequest registro = new RegisterRequest("ana", "clave1234", null, null, "a@b.c", null, "es");
        AuthenticateRequest login = new AuthenticateRequest("ana", "clave1234");

        assertThat(registro.toString()).doesNotContain("clave1234");
        assertThat(login.toString()).doesNotContain("clave1234");
    }

    // Un registro válido al que se le cambia un solo campo.
    private static String json(String campo, String valor) {
        String base = "{\"login\":\"ana\",\"password\":\"clave1234\",\"firstName\":\"Ana\",\"lastName\":\"Paz\","
                + "\"email\":\"ana@correo.com\",\"imageUrl\":\"http://img\",\"langKey\":\"es\"}";
        return base.replaceAll("\"" + campo + "\":\"[^\"]*\"", "\"" + campo + "\":\"" + valor + "\"");
    }
}
