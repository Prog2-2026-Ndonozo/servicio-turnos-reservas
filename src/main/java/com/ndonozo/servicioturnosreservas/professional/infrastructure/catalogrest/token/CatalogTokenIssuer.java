package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.interfaces.RSAPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public class CatalogTokenIssuer {

    static final Duration VIGENCIA = Duration.ofMinutes(5);
    private static final String SUBJECT = "servicio-turnos";
    private static final String SCOPE = "catalog:read";

    private final RSAPrivateKey privateKey;
    private final String issuer;
    private final String audience;
    private final Clock clock;

    public CatalogTokenIssuer(RSAPrivateKey privateKey, String issuer, String audience, Clock clock) {
        this.privateKey = privateKey;
        this.issuer = issuer;
        this.audience = audience;
        this.clock = clock;
    }

    public IssuedToken issue() {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(VIGENCIA);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(SUBJECT)
                .claim("scope", SCOPE)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(privateKey));
            return new IssuedToken(jwt.serialize(), expiresAt);
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el token para el catálogo", e);
        }
    }
}