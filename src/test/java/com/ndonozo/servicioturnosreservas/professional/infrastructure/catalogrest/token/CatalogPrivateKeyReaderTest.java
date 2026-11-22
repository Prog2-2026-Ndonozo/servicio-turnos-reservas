package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.token;

import com.ndonozo.servicioturnosreservas.support.TestRsaKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogPrivateKeyReaderTest {

    @TempDir
    Path carpeta;

    @Test
    void lee_una_clave_privada_RSA_de_2048_bits_en_PKCS8() {
        RSAPrivateKey key = CatalogPrivateKeyReader.read(TestRsaKeys.privateKeyPemFile());

        assertThat(key.getModulus()).isEqualTo(TestRsaKeys.privateKey().getModulus());
    }

    @Test
    void sin_ruta_falla_diciendo_que_variable_falta() {
        assertThatThrownBy(() -> CatalogPrivateKeyReader.read(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CATALOGO_PRIVATE_KEY_FILE");
    }

    @Test
    void un_archivo_inexistente_falla_diciendo_la_ruta() {
        Path inexistente = carpeta.resolve("no-esta.pem");

        assertThatThrownBy(() -> CatalogPrivateKeyReader.read(inexistente))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(inexistente.toString());
    }

    @Test
    void un_archivo_que_no_es_una_clave_falla_sin_mostrar_su_contenido() throws IOException {
        Path basura = carpeta.resolve("basura.pem");
        Files.writeString(basura, "contenido-secreto-que-no-debe-aparecer");

        assertThatThrownBy(() -> CatalogPrivateKeyReader.read(basura))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PKCS#8")
                .hasMessageNotContaining("contenido-secreto");
    }

    @Test
    void una_clave_de_menos_de_2048_bits_se_rechaza() throws IOException {
        Path corta = carpeta.resolve("corta.pem");
        Files.writeString(corta, TestRsaKeys.pem(TestRsaKeys.generate(1024)));

        assertThatThrownBy(() -> CatalogPrivateKeyReader.read(corta))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2048");
    }

    @Test
    void una_clave_publica_no_sirve_como_privada() throws IOException {
        Path publica = carpeta.resolve("publica.pem");
        Files.writeString(publica, "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(TestRsaKeys.publicKey().getEncoded())
                + "\n-----END PUBLIC KEY-----\n");

        assertThatThrownBy(() -> CatalogPrivateKeyReader.read(publica)).isInstanceOf(IllegalStateException.class);
    }
}
