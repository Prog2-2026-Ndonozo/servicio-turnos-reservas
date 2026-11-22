package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.token;

import org.springframework.security.converter.RsaKeyConverters;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;

public final class CatalogPrivateKeyReader {

    static final int MIN_KEY_BITS = 2048;

    private CatalogPrivateKeyReader() {
    }

    public static RSAPrivateKey read(Path file) {
        if (file == null || file.toString().isBlank()) {
            throw new IllegalStateException(
                    "Falta CATALOGO_PRIVATE_KEY_FILE: ruta al archivo PEM con la clave privada RS256 de turnos");
        }
        if (!Files.isReadable(file)) {
            throw new IllegalStateException("No se puede leer la clave privada del catálogo en " + file
                    + " (¿existe y el usuario de la aplicación tiene permiso de lectura?)");
        }
        RSAPrivateKey key;
        try (InputStream in = Files.newInputStream(file)) {
            key = RsaKeyConverters.pkcs8().convert(in);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("El archivo " + file + " no tiene una clave privada RSA válida en "
                    + "formato PKCS#8 (-----BEGIN PRIVATE KEY-----)");
        }
        if (key == null || key.getModulus().bitLength() < MIN_KEY_BITS) {
            throw new IllegalStateException("La clave privada del catálogo debe ser RSA de al menos "
                    + MIN_KEY_BITS + " bits");
        }
        return key;
    }
}