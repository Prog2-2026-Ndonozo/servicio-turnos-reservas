package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.token;

import com.ndonozo.servicioturnosreservas.support.TestRsaKeys;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogTokenIssuerTest {

    private static final Instant AHORA = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final CatalogTokenIssuer issuer = new CatalogTokenIssuer(
            TestRsaKeys.privateKey(), "servicio-turnos", "servicio-catalogo", Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    void el_token_se_verifica_con_la_clave_publica_y_lleva_los_claims_del_contrato() {
        Jwt jwt = decoder(TestRsaKeys.publicKey()).decode(issuer.issue().value());

        assertThat(jwt.getClaims().get("iss").toString()).isEqualTo("servicio-turnos");
        assertThat(jwt.getAudience()).containsExactly("servicio-catalogo");
        assertThat(jwt.getSubject()).isEqualTo("servicio-turnos");
        assertThat(jwt.<String>getClaim("scope")).isEqualTo("catalog:read");
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    void se_firma_con_RS256() {
        Jwt jwt = decoder(TestRsaKeys.publicKey()).decode(issuer.issue().value());

        assertThat(jwt.getHeaders().get("alg")).isEqualTo("RS256");
    }

    @Test
    void vence_a_los_5_minutos() {
        IssuedToken token = issuer.issue();
        Jwt jwt = decoder(TestRsaKeys.publicKey()).decode(token.value());

        assertThat(token.expiresAt()).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));
        assertThat(jwt.getIssuedAt()).isEqualTo(AHORA);
        assertThat(jwt.getExpiresAt()).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));
    }

    @Test
    void no_lleva_userId_porque_el_token_es_unico_y_compartido() {
        Jwt jwt = decoder(TestRsaKeys.publicKey()).decode(issuer.issue().value());

        assertThat(jwt.hasClaim("userId")).isFalse();
    }

    @Test
    void cada_token_tiene_un_jti_distinto() {
        Jwt uno = decoder(TestRsaKeys.publicKey()).decode(issuer.issue().value());
        Jwt otro = decoder(TestRsaKeys.publicKey()).decode(issuer.issue().value());

        assertThat(uno.getId()).isNotEqualTo(otro.getId());
    }

    @Test
    void no_se_verifica_con_la_clave_publica_de_otro_par() {
        String token = issuer.issue().value();
        RSAPublicKey otraPublica = (RSAPublicKey) TestRsaKeys.generate(2048).getPublic();

        assertThatThrownBy(() -> decoder(otraPublica).decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void el_emisor_y_la_audiencia_salen_de_la_configuracion() {
        CatalogTokenIssuer otro = new CatalogTokenIssuer(TestRsaKeys.privateKey(), "otro-emisor", "otra-audiencia",
                Clock.fixed(AHORA, ZoneOffset.UTC));

        Jwt jwt = decoder(TestRsaKeys.publicKey()).decode(otro.issue().value());

        assertThat(jwt.getClaims().get("iss").toString()).isEqualTo("otro-emisor");
        assertThat(jwt.getAudience()).containsExactly("otra-audiencia");
    }

    @Test
    void el_toString_del_token_no_revela_su_valor() {
        IssuedToken token = issuer.issue();

        assertThat(token.toString()).doesNotContain(token.value());
    }

    // El decodificador solo valida la firma; los claims se comprueban arriba.
    private static NimbusJwtDecoder decoder(RSAPublicKey publicKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        return decoder;
    }
}
