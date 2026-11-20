package com.ndonozo.servicioturnosreservas.config;

import com.ndonozo.servicioturnosreservas.user.domain.model.User;
import com.ndonozo.servicioturnosreservas.user.infrastructure.security.JwtTokenIssuer;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
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
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba el servidor de recursos con el decodificador real que arma SecurityConfig (no con el post-procesador jwt() de
 * Spring Security, que lo saltearía). Los tokens salen del JwtTokenIssuer real, igual que en producción.
 */
@WebMvcTest(JwtSecurityTest.RutaProtegida.class)
@Import({SecurityConfig.class, ProblemJsonSecurityHandlers.class, JwtSecurityTest.RutaProtegida.class})
@TestPropertySource(properties = "app.security.jwt.secret=" + JwtSecurityTest.SECRETO)
class JwtSecurityTest {

    // 64 bytes en Base64, solo para pruebas
    static final String SECRETO =
            "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBw==";
    private static final byte[] CLAVE = Base64.getDecoder().decode(SECRETO);
    private static final User ANA = User.builder().id(42L).login("ana").authorities(Set.of(User.ROLE_USER)).build();

    /** Controller de prueba: T3 todavía no tiene ningún endpoint protegido real. */
    @RestController
    static class RutaProtegida {
        @GetMapping("/api/protegida")
        String quienSoy(Authentication authentication) {
            return authentication.getName();
        }

        @PostMapping("/api/register")
        String registro() {
            return "registro";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    // ---------- token válido ----------

    @Test
    void un_token_valido_accede_y_la_identidad_sale_del_token() throws Exception {
        mockMvc.perform(get("/api/protegida").header(HttpHeaders.AUTHORIZATION, bearer(tokenValido())))
                .andExpect(status().isOk())
                .andExpect(content().string("ana"));
    }

    // ---------- rechazos: 401 en problem+json con code estable ----------

    @Test
    void sin_token_responde_401_UNAUTHENTICATED_en_problem_json() throws Exception {
        mockMvc.perform(get("/api/protegida"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void un_token_firmado_con_otra_clave_responde_401() throws Exception {
        byte[] otraClave = new byte[64];
        java.util.Arrays.fill(otraClave, (byte) 9);
        String token = firmar(otraClave, "HmacSHA512", MacAlgorithm.HS512, Duration.ofHours(1));

        rechaza(token);
    }

    @Test
    void un_token_vencido_responde_401() throws Exception {
        rechaza(vencidoHace(Duration.ofHours(1)));
    }

    @Test
    void un_token_vencido_hace_un_segundo_tambien_se_rechaza_porque_no_hay_tolerancia_de_reloj() throws Exception {
        rechaza(vencidoHace(Duration.ofSeconds(1)));
    }

    @Test
    void un_token_con_el_payload_alterado_responde_401() throws Exception {
        String[] partes = tokenValido().split("\\.");
        String payloadFalso = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"admin\",\"uid\":1,\"auth\":\"ROLE_ADMIN\",\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));

        rechaza(partes[0] + "." + payloadFalso + "." + partes[2]);
    }

    @Test
    void un_token_con_otro_algoritmo_aunque_use_la_misma_clave_responde_401() throws Exception {
        rechaza(firmar(CLAVE, "HmacSHA256", MacAlgorithm.HS256, Duration.ofHours(1)));
    }

    @Test
    void un_token_sin_firma_alg_none_responde_401() throws Exception {
        String encabezado = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"ana\",\"uid\":42,\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));

        rechaza(encabezado + "." + payload + ".");
    }

    @Test
    void un_texto_que_no_es_un_jwt_responde_401() throws Exception {
        rechaza("esto-no-es-un-jwt");
    }

    @Test
    void otro_esquema_de_autorizacion_responde_401() throws Exception {
        mockMvc.perform(get("/api/protegida").header(HttpHeaders.AUTHORIZATION, "Basic YW5hOmNsYXZl"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void el_401_no_revela_por_que_fallo_el_token() throws Exception {
        String vencido = vencidoHace(Duration.ofHours(1));

        mockMvc.perform(get("/api/protegida").header(HttpHeaders.AUTHORIZATION, bearer(vencido)))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.detail").value("Token ausente, inválido o vencido"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("expired"))));
    }

    // ---------- rutas públicas ----------

    @Test
    void las_rutas_publicas_no_piden_token() throws Exception {
        mockMvc.perform(post("/api/register")).andExpect(status().isOk());
    }

    @Test
    void un_token_vencido_en_el_header_no_impide_usar_una_ruta_publica() throws Exception {
        String vencido = vencidoHace(Duration.ofHours(1));

        mockMvc.perform(post("/api/register").header(HttpHeaders.AUTHORIZATION, bearer(vencido)))
                .andExpect(status().isOk());
    }

    @Test
    void la_ruta_publica_lo_es_solo_para_el_metodo_declarado() throws Exception {
        mockMvc.perform(get("/api/register")).andExpect(status().isUnauthorized());
    }

    // ---------- soporte ----------

    private void rechaza(String token) throws Exception {
        mockMvc.perform(get("/api/protegida").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String tokenValido() {
        return new JwtTokenIssuer(SECRETO, Duration.ofHours(1)).issue(ANA).value();
    }

    // Un token que venció hace "tiempo". JwtTokenIssuer no sirve: Spring no deja emitir con exp anterior a iat.
    private static String vencidoHace(Duration tiempo) {
        Instant vencimiento = Instant.now().minus(tiempo);
        return firmar(CLAVE, "HmacSHA512", MacAlgorithm.HS512, vencimiento.minus(Duration.ofMinutes(5)), vencimiento);
    }

    private static String firmar(byte[] clave, String algoritmoJca, MacAlgorithm algoritmo, Duration vigencia) {
        Instant ahora = Instant.now();
        return firmar(clave, algoritmoJca, algoritmo, ahora, ahora.plus(vigencia));
    }

    private static String firmar(byte[] clave, String algoritmoJca, MacAlgorithm algoritmo,
                                 Instant emitido, Instant vencimiento) {
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("ana").claim("uid", 42L)
                .issuedAt(emitido).expiresAt(vencimiento).build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(clave, algoritmoJca)));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(algoritmo).build(), claims)).getTokenValue();
    }
}
