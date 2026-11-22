package com.ndonozo.servicioturnosreservas.professional.infrastructure.web;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.jayway.jsonpath.JsonPath;
import com.ndonozo.servicioturnosreservas.support.TestRsaKeys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T5 de punta a punta: un usuario real se registra e inicia sesión, y con su JWT consulta a turnos, que le reenvía el
 * pedido a un catálogo simulado (WireMock) con el JWT técnico RS256. Comprueba qué recibe el catálogo y qué recibe la app.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "app.security.jwt.secret=" + ProfessionalApiIntegrationTest.SECRETO,
        "app.catalog.read-timeout=500ms"})
class ProfessionalApiIntegrationTest {

    // 64 bytes en Base64, solo para pruebas
    static final String SECRETO =
            "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBw==";

    private static final String BUSQUEDA = "/api/professionals/search";
    private static final String CATEGORIAS = "/api/professional-categories";

    static final WireMockServer CATALOGO = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        CATALOGO.start();
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configuracion(DynamicPropertyRegistry registry) {
        registry.add("app.catalog.jwt.private-key-file", () -> TestRsaKeys.privateKeyPemFile().toString());
        registry.add("app.catalog.base-url", () -> "http://localhost:" + CATALOGO.port());
    }

    @Autowired
    private MockMvc mockMvc;

    private String tokenDeUsuario;

    @BeforeEach
    void usuarioLogueadoYCatalogoLimpio() throws Exception {
        CATALOGO.resetAll();
        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"login\":\"kmp\",\"password\":\"clave1234\",\"email\":\"kmp@correo.com\",\"langKey\":\"es\"}"));
        String cuerpo = mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"kmp\",\"password\":\"clave1234\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        tokenDeUsuario = JsonPath.read(cuerpo, "$.id_token");
    }

    @AfterAll
    static void apagaElCatalogo() {
        CATALOGO.stop();
    }

    // ---------- reenvío al catálogo ----------

    @Test
    void la_busqueda_se_reenvia_al_catalogo_con_los_mismos_filtros_y_la_respuesta_llega_en_su_orden() throws Exception {
        responde(BUSQUEDA, 200, """
                [{"id":15,"categoryId":101,"firstName":"Ana","lastName":"Gomez","enabled":true,"campoNuevo":"x"},
                 {"id":7,"categoryId":101,"firstName":"Luis","lastName":"Gomez","enabled":false}]""");

        mockMvc.perform(getAutenticado(BUSQUEDA).queryParam("categoryId", "101").queryParam("name", "ana & luis")
                        .queryParam("enabled", "true").queryParam("availableOn", "2026-11-23")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(15))
                .andExpect(jsonPath("$[0].firstName").value("Ana"))
                .andExpect(jsonPath("$[1].id").value(7))
                .andExpect(jsonPath("$[1].enabled").value(false))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("campoNuevo"))));

        CATALOGO.verify(1, getRequestedFor(urlPathEqualTo(BUSQUEDA))
                .withQueryParam("categoryId", equalTo("101"))
                .withQueryParam("name", equalTo("ana & luis"))
                .withQueryParam("enabled", equalTo("true"))
                .withQueryParam("availableOn", equalTo("2026-11-23")));
    }

    @Test
    void las_categorias_se_reenvian_al_catalogo() throws Exception {
        responde(CATEGORIAS, 200, """
                [{"id":101,"name":"Cardiologia","description":"Atencion cardiovascular","enabled":true}]""");

        mockMvc.perform(getAutenticado(CATEGORIAS).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Cardiologia"));

        CATALOGO.verify(1, getRequestedFor(urlPathEqualTo(CATEGORIAS)));
    }

    @Test
    void cada_consulta_vuelve_a_preguntarle_al_catalogo() throws Exception {
        responde(CATEGORIAS, 200, "[]");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(getAutenticado(CATEGORIAS).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario))
                    .andExpect(status().isOk());
        }

        CATALOGO.verify(3, getRequestedFor(urlPathEqualTo(CATEGORIAS)));
    }

    // ---------- identidad: el catálogo nunca ve el token del usuario ----------

    @Test
    void el_catalogo_recibe_el_token_tecnico_RS256_de_turnos_y_nunca_el_del_usuario() throws Exception {
        responde(CATEGORIAS, 200, "[]");

        mockMvc.perform(getAutenticado(CATEGORIAS).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario))
                .andExpect(status().isOk());

        String recibido = tokenRecibidoPorElCatalogo();
        assertThat(recibido).isNotEqualTo(tokenDeUsuario);
        Jwt tecnico = NimbusJwtDecoder.withPublicKey(TestRsaKeys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build().decode(recibido);
        assertThat(tecnico.getClaims().get("iss").toString()).isEqualTo("servicio-turnos");
        assertThat(tecnico.getAudience()).containsExactly("servicio-catalogo");
        assertThat(tecnico.<String>getClaim("scope")).isEqualTo("catalog:read");
        assertThat(tecnico.hasClaim("userId")).isFalse();
        assertThat(tecnico.hasClaim("uid")).isFalse();
    }

    @Test
    void el_token_tecnico_no_sirve_para_entrar_a_turnos() throws Exception {
        responde(CATEGORIAS, 200, "[]");
        mockMvc.perform(getAutenticado(CATEGORIAS).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario))
                .andExpect(status().isOk());
        String tecnico = tokenRecibidoPorElCatalogo();

        mockMvc.perform(getAutenticado(CATEGORIAS).header(HttpHeaders.AUTHORIZATION, "Bearer " + tecnico))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ---------- protección: se rechaza antes de reenviar ----------

    @Test
    void sin_token_o_con_token_alterado_responde_401_y_el_catalogo_no_recibe_nada() throws Exception {
        for (String ruta : new String[]{BUSQUEDA, CATEGORIAS}) {
            mockMvc.perform(getAutenticado(ruta)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mockMvc.perform(getAutenticado(ruta).header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + tokenDeUsuario.substring(0, tokenDeUsuario.length() - 3) + "abc"))
                    .andExpect(status().isUnauthorized());
        }

        assertThat(CATALOGO.getAllServeEvents()).isEmpty();
    }

    @Test
    void un_filtro_invalido_responde_400_y_el_catalogo_no_recibe_nada() throws Exception {
        mockMvc.perform(getAutenticado(BUSQUEDA).queryParam("categoryId", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(CATALOGO.getAllServeEvents()).isEmpty();
    }

    @Test
    void la_documentacion_de_la_api_no_es_publica_fuera_del_perfil_dev() throws Exception {
        mockMvc.perform(getAutenticado("/v3/api-docs")).andExpect(status().isUnauthorized());
        mockMvc.perform(getAutenticado("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    }

    // ---------- errores del catálogo hacia la app ----------

    @Test
    void catalogo_con_error_interno_responde_503_CATALOG_UNAVAILABLE() throws Exception {
        responde(CATEGORIAS, 500, "");

        mockMvc.perform(getAutenticado(CATEGORIAS).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
    }

    @Test
    void catalogo_demasiado_lento_responde_503_CATALOG_UNAVAILABLE() throws Exception {
        CATALOGO.stubFor(get(urlPathEqualTo(CATEGORIAS)).willReturn(aResponse().withStatus(200).withFixedDelay(2000)
                .withHeader("Content-Type", "application/json").withBody("[]")));

        mockMvc.perform(getAutenticado(CATEGORIAS).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
    }

    @Test
    void catalogo_que_rechaza_el_token_tecnico_dos_veces_responde_502_CATALOG_ERROR() throws Exception {
        responde(CATEGORIAS, 401, "");

        mockMvc.perform(getAutenticado(CATEGORIAS).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CATALOG_ERROR"));
        assertThat(CATALOGO.getAllServeEvents()).hasSize(2); // el pedido y el único reintento
    }

    @Test
    void catalogo_que_responde_403_responde_502_CATALOG_ERROR() throws Exception {
        responde(BUSQUEDA, 403, "");

        mockMvc.perform(getAutenticado(BUSQUEDA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CATALOG_ERROR"));
    }

    // ---------- soporte ----------

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder getAutenticado(String ruta) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(ruta);
    }

    private void responde(String ruta, int estado, String cuerpo) {
        CATALOGO.stubFor(get(urlPathEqualTo(ruta)).willReturn(aResponse().withStatus(estado)
                .withHeader("Content-Type", estado >= 400 ? "application/problem+json" : "application/json")
                .withBody(cuerpo)));
    }

    private String tokenRecibidoPorElCatalogo() {
        var pedido = CATALOGO.getAllServeEvents().getLast().getRequest();
        return pedido.getHeader("Authorization").substring("Bearer ".length());
    }
}
