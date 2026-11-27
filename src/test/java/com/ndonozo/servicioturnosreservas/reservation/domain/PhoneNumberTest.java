package com.ndonozo.servicioturnosreservas.reservation.domain;

import com.ndonozo.servicioturnosreservas.reservation.domain.model.PhoneNumber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** La misma regla que aplica la cátedra (I §15.5): se quitan separadores y queda ^\+?[0-9]{7,15}$. */
class PhoneNumberTest {

    @Test
    void quita_espacios_guiones_parentesis_y_puntos() {
        assertThat(PhoneNumber.parse("+54 9 (261) 555-1234").orElseThrow().value()).isEqualTo("+5492615551234");
        assertThat(PhoneNumber.parse("261.555.1234").orElseThrow().value()).isEqualTo("2615551234");
        assertThat(PhoneNumber.parse("  2615551234  ").orElseThrow().value()).isEqualTo("2615551234");
    }

    @Test
    void acepta_de_7_a_15_digitos_con_un_mas_inicial_opcional() {
        assertThat(PhoneNumber.parse("1234567")).isPresent();
        assertThat(PhoneNumber.parse("123456789012345")).isPresent();
        assertThat(PhoneNumber.parse("+123456789012345")).isPresent();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "123456", "1234567890123456", "+", "abc1234567", "261-555-12x4", "26+15551234",
            "++5492615551234", "261/5551234", "+54 9 261 555-1234 int 5"})
    void rechaza_lo_que_no_cumple(String crudo) {
        assertThat(PhoneNumber.parse(crudo)).isEmpty();
    }
}
