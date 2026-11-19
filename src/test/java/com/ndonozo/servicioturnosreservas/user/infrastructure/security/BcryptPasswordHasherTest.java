package com.ndonozo.servicioturnosreservas.user.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BcryptPasswordHasherTest {

    private final BcryptPasswordHasher hasher = new BcryptPasswordHasher();

    @Test
    void el_hash_no_es_la_clave_en_texto_plano() {
        String hash = hasher.hash("clave-secreta");

        assertThat(hash).isNotEqualTo("clave-secreta").startsWith("$2");
    }

    @Test
    void el_hash_entra_en_la_columna_de_60_caracteres() {
        assertThat(hasher.hash("clave-secreta")).hasSize(60);
    }

    @Test
    void la_clave_correcta_coincide_con_su_hash() {
        String hash = hasher.hash("clave-secreta");

        assertThat(hasher.matches("clave-secreta", hash)).isTrue();
    }

    @Test
    void una_clave_incorrecta_no_coincide() {
        String hash = hasher.hash("clave-secreta");

        assertThat(hasher.matches("otra-clave", hash)).isFalse();
    }

    @Test
    void la_misma_clave_genera_hashes_distintos_por_la_sal() {
        assertThat(hasher.hash("clave-secreta")).isNotEqualTo(hasher.hash("clave-secreta"));
    }
}
