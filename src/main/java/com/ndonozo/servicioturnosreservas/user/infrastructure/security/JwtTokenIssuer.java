package com.ndonozo.servicioturnosreservas.user.infrastructure.security;

import com.ndonozo.servicioturnosreservas.user.domain.model.AuthToken;
import com.ndonozo.servicioturnosreservas.user.domain.model.User;
import com.ndonozo.servicioturnosreservas.user.domain.ports.out.TokenIssuer;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Component
public class JwtTokenIssuer implements TokenIssuer {


    private static final int MIN_SECRET_BYTES = 64;

    private final JwtEncoder encoder;
    private final Duration expiration;

    public JwtTokenIssuer(@Value("${app.security.jwt.secret}") String secret,
                          @Value("${app.security.jwt.expiration:24h}") Duration expiration) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(secret)));
        this.expiration = expiration;
    }

    @Override
    public AuthToken issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getLogin())
                .claim("auth", String.join(" ", user.getAuthorities()))
                .claim("uid", user.getId())
                .issuedAt(now)
                .expiresAt(now.plus(expiration))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS512).build();
        return new AuthToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue());
    }

    private static SecretKeySpec secretKey(String secret) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET no es un Base64 válido");
        }
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos " + MIN_SECRET_BYTES + " bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA512");
    }
}
