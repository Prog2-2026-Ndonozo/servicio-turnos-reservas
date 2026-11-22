package com.ndonozo.servicioturnosreservas.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/**
 * Par de claves RS256 de prueba, generado en memoria en cada ejecución. Nunca se versiona una clave privada, ni siquiera
 * de prueba: el archivo PEM que algunos tests necesitan se escribe en un temporal y se borra al terminar.
 */
public final class TestRsaKeys {

    private static final KeyPair KEY_PAIR = generate(2048);
    private static Path pemFile;

    private TestRsaKeys() {
    }

    public static RSAPrivateKey privateKey() {
        return (RSAPrivateKey) KEY_PAIR.getPrivate();
    }

    public static RSAPublicKey publicKey() {
        return (RSAPublicKey) KEY_PAIR.getPublic();
    }

    public static KeyPair generate(int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String pem(KeyPair keyPair) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }

    /** Ruta de un PEM temporal con la clave privada de prueba (se crea una sola vez por ejecución). */
    public static synchronized Path privateKeyPemFile() {
        if (pemFile == null) {
            try {
                pemFile = Files.createTempFile("turnos-test-key-", ".pem");
                Files.writeString(pemFile, pem(KEY_PAIR));
                pemFile.toFile().deleteOnExit();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return pemFile;
    }
}
