package com.ndonozo.servicioturnosreservas.user.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T2 de punta a punta: HTTP real (MockMvc) -> controller -> fachada -> casos de uso -> adaptadores reales -> PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "app.security.jwt.secret=" + UserApiIntegrationTest.SECRETO_DE_PRUEBA)
class UserApiIntegrationTest {

    // 64 bytes en Base64, solo para pruebas
    static final String SECRETO_DE_PRUEBA =
            "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBw==";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void registrarse_e_iniciar_sesion_devuelve_un_token_valido_del_usuario() throws Exception {
        registrar("ana", "ana@correo.com", "clave1234").andExpect(status().isCreated());

        MvcResult resultado = autenticar("ana", "clave1234").andExpect(status().isOk()).andReturn();

        String token = com.jayway.jsonpath.JsonPath.read(resultado.getResponse().getContentAsString(), "$.id_token");
        Jwt jwt = decoder().decode(token);
        Long id = jdbc.queryForObject("SELECT id FROM app_user WHERE login = 'ana'", Long.class);
        assertThat(jwt.getSubject()).isEqualTo("ana");
        assertThat(jwt.<Long>getClaim("uid")).isEqualTo(id);
        assertThat(jwt.<String>getClaim("auth")).isEqualTo("ROLE_USER");
    }

    @Test
    void el_usuario_queda_activo_con_ROLE_USER_y_la_clave_hasheada() throws Exception {
        registrar("beto", "beto@correo.com", "clave1234").andExpect(status().isCreated());

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT activated, password_hash FROM app_user WHERE login = 'beto'");
        List<String> autoridades = jdbc.queryForList(
                "SELECT authority FROM user_authority ua JOIN app_user u ON u.id = ua.user_id WHERE u.login = 'beto'",
                String.class);
        assertThat(fila.get("activated")).isEqualTo(true);
        assertThat((String) fila.get("password_hash")).startsWith("$2").isNotEqualTo("clave1234");
        assertThat(autoridades).containsExactly("ROLE_USER");
    }

    @Test
    void login_y_email_se_guardan_en_minusculas_y_el_login_no_distingue_mayusculas() throws Exception {
        registrar("Carla", "Carla@Correo.COM", "clave1234").andExpect(status().isCreated());

        Map<String, Object> fila = jdbc.queryForMap("SELECT login, email FROM app_user WHERE login = 'carla'");
        assertThat(fila.get("email")).isEqualTo("carla@correo.com");
        autenticar("CARLA", "clave1234").andExpect(status().isOk());
    }

    @Test
    void una_clave_de_72_bytes_registra_e_inicia_sesion() throws Exception {
        String larga = "ñ".repeat(36);
        registrar("dora", "dora@correo.com", larga).andExpect(status().isCreated());

        autenticar("dora", larga).andExpect(status().isOk());
    }

    @Test
    void un_login_repetido_responde_409_aunque_cambien_las_mayusculas() throws Exception {
        registrar("eli", "eli@correo.com", "clave1234").andExpect(status().isCreated());

        registrar("ELI", "otro@correo.com", "clave1234")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOGIN_ALREADY_USED"));
    }

    @Test
    void un_email_repetido_responde_409() throws Exception {
        registrar("fede", "fede@correo.com", "clave1234").andExpect(status().isCreated());

        registrar("fede2", "FEDE@correo.com", "clave1234")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    void usuario_inexistente_y_clave_incorrecta_responden_exactamente_igual() throws Exception {
        registrar("gus", "gus@correo.com", "clave1234").andExpect(status().isCreated());

        String claveMala = autenticar("gus", "otra-clave").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String noExiste = autenticar("nadie", "clave1234").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(claveMala).isEqualTo(noExiste).contains("BAD_CREDENTIALS");
    }

    @Test
    void la_respuesta_de_login_no_contiene_la_clave_ni_el_hash() throws Exception {
        registrar("hugo", "hugo@correo.com", "clave1234").andExpect(status().isCreated());

        String cuerpo = autenticar("hugo", "clave1234").andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("clave1234").doesNotContain("$2");
    }

    // ---------- T3: el token que emite el login es el que la seguridad acepta ----------

    @Test
    void el_token_del_login_pasa_la_seguridad_y_sin_token_no() throws Exception {
        registrar("ines", "ines@correo.com", "clave1234").andExpect(status().isCreated());
        String token = com.jayway.jsonpath.JsonPath.read(
                autenticar("ines", "clave1234").andReturn().getResponse().getContentAsString(), "$.id_token");

        // No hay ningún endpoint protegido real todavía: 404 significa que la seguridad lo dejó pasar.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/no-existe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/no-existe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void un_token_de_login_alterado_no_pasa_la_seguridad() throws Exception {
        registrar("jaime", "jaime@correo.com", "clave1234").andExpect(status().isCreated());
        String token = com.jayway.jsonpath.JsonPath.read(
                autenticar("jaime", "clave1234").andReturn().getResponse().getContentAsString(), "$.id_token");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/no-existe")
                        .header("Authorization", "Bearer " + token.substring(0, token.length() - 3) + "abc"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions registrar(String login, String email, String password)
            throws Exception {
        return mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"login\":\"%s\",\"password\":\"%s\",\"email\":\"%s\",\"langKey\":\"es\"}"
                        .formatted(login, password, email)));
    }

    private org.springframework.test.web.servlet.ResultActions autenticar(String username, String password)
            throws Exception {
        return mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON).content(
                "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    private static NimbusJwtDecoder decoder() {
        return NimbusJwtDecoder.withSecretKey(
                new SecretKeySpec(Base64.getDecoder().decode(SECRETO_DE_PRUEBA), "HmacSHA512"))
                .macAlgorithm(MacAlgorithm.HS512).build();
    }
}
