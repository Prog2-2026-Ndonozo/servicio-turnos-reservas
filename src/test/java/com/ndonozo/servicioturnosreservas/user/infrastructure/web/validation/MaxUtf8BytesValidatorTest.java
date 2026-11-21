package com.ndonozo.servicioturnosreservas.user.infrastructure.web.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MaxUtf8BytesValidatorTest {

    private final MaxUtf8BytesValidator validator = validadorCon(72);

    @Test
    void acepta_exactamente_el_maximo_de_bytes() {
        assertThat(validator.isValid("a".repeat(72), null)).isTrue();
    }

    @Test
    void rechaza_un_byte_de_mas() {
        assertThat(validator.isValid("a".repeat(73), null)).isFalse();
    }

    @Test
    void cuenta_bytes_y_no_caracteres() {
        assertThat(validator.isValid("ñ".repeat(36), null)).isTrue();  // 72 bytes
        assertThat(validator.isValid("ñ".repeat(37), null)).isFalse(); // 74 bytes, pero solo 37 caracteres
    }

    @Test
    void null_es_valido_porque_eso_lo_decide_NotBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    private static MaxUtf8BytesValidator validadorCon(int max) {
        MaxUtf8Bytes anotacion = mock(MaxUtf8Bytes.class);
        org.mockito.Mockito.when(anotacion.value()).thenReturn(max);
        MaxUtf8BytesValidator validator = new MaxUtf8BytesValidator();
        validator.initialize(anotacion);
        return validator;
    }
}
