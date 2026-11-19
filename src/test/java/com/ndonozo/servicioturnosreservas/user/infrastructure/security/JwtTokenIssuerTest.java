package com.ndonozo.servicioturnosreservas.user.infrastructure.security;

import com.ndonozo.servicioturnosreservas.user.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenIssuerTest {

    private static final byte[] CLAVE = new byte[64];
    private static final byte[] OTRA_CLAVE = new byte[64];

    static {
        java.util.Arrays.fill(CLAVE, (byte) 7);
        java.util.Arrays.fill(OTRA_CLAVE, (byte) 9);
    }

    private final JwtTokenIssuer issuer = new JwtTokenIssuer(base64(CLAVE), Duration.ofHours(24));

    private final User ana = User.builder().id(42L).login("ana").authorities(Set.of(User.ROLE_USER)).build();

    @Test
    void el_token_se_valida_con_la_misma_clave_y_lleva_los_claims() {
        String token = issuer.issue(ana).value();

        Jwt jwt = decoder(CLAVE).decode(token);

        assertThat(jwt.getSubject()).isEqualTo("ana");
        assertThat(jwt.<String>getClaim("auth")).isEqualTo("ROLE_USER");
        assertThat(jwt.<Long>getClaim("uid")).isEqualTo(42L);
    }

    @Test
    void el_token_se_firma_con_HS512() {
        Jwt jwt = decoder(CLAVE).decode(issuer.issue(ana).value());

        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS512");
    }

    @Test
    void el_token_vence_a_las_24_horas() {
        Jwt jwt = decoder(CLAVE).decode(issuer.issue(ana).value());

        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void con_otra_clave_el_token_no_valida() {
        String token = issuer.issue(ana).value();

        assertThatThrownBy(() -> decoder(OTRA_CLAVE).decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void un_token_alterado_no_valida() {
        String token = issuer.issue(ana).value();
        String alterado = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> decoder(CLAVE).decode(alterado)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rechaza_un_secreto_de_menos_de_64_bytes() {
        assertThatThrownBy(() -> new JwtTokenIssuer(base64(new byte[32]), Duration.ofHours(24)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("64 bytes");
    }

    @Test
    void rechaza_un_secreto_que_no_es_base64() {
        assertThatThrownBy(() -> new JwtTokenIssuer("esto no es base64 !!!", Duration.ofHours(24)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void el_mensaje_de_error_no_revela_el_secreto() {
        String secretoCorto = base64(new byte[32]);

        assertThatThrownBy(() -> new JwtTokenIssuer(secretoCorto, Duration.ofHours(24)))
                .hasMessageNotContaining(secretoCorto);
    }

    private static NimbusJwtDecoder decoder(byte[] clave) {
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(clave, "HmacSHA512"))
                .macAlgorithm(MacAlgorithm.HS512).build();
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
