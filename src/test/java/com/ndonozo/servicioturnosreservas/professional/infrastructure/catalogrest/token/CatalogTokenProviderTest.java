package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.token;

import com.ndonozo.servicioturnosreservas.support.TestRsaKeys;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogTokenProviderTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-20T12:00:00Z"));
    private final CatalogTokenIssuer issuer =
            new CatalogTokenIssuer(TestRsaKeys.privateKey(), "servicio-turnos", "servicio-catalogo", clock);
    private final CatalogTokenProvider provider = new CatalogTokenProvider(issuer, clock);

    @Test
    void reutiliza_el_mismo_token_mientras_no_se_acerque_el_vencimiento() {
        String primero = provider.token();

        clock.advance(Duration.ofMinutes(2));

        assertThat(provider.token()).isEqualTo(primero);
    }

    @Test
    void sigue_usando_el_token_hasta_un_segundo_antes_del_margen_de_30_segundos() {
        String primero = provider.token();

        clock.advance(Duration.ofSeconds(4 * 60 + 29));

        assertThat(provider.token()).isEqualTo(primero);
    }

    @Test
    void emite_uno_nuevo_cuando_faltan_30_segundos_para_que_venza() {
        String primero = provider.token();

        clock.advance(Duration.ofSeconds(4 * 60 + 30));

        assertThat(provider.token()).isNotEqualTo(primero);
    }

    @Test
    void emite_uno_nuevo_si_el_anterior_ya_vencio() {
        String primero = provider.token();

        clock.advance(Duration.ofMinutes(10));

        assertThat(provider.token()).isNotEqualTo(primero);
    }

    @Test
    void al_descartar_el_token_rechazado_el_proximo_pedido_emite_otro() {
        String rechazado = provider.token();

        provider.discard(rechazado);

        assertThat(provider.token()).isNotEqualTo(rechazado);
    }

    @Test
    void descartar_un_token_que_ya_no_es_el_vigente_no_tira_el_nuevo() {
        String viejo = provider.token();
        provider.discard(viejo);
        String nuevo = provider.token();

        provider.discard(viejo); // otro hilo recibió el 401 del token viejo después de que se emitió el nuevo

        assertThat(provider.token()).isEqualTo(nuevo);
    }

    @Test
    void con_muchos_hilos_a_la_vez_se_emite_un_solo_token() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<String>> tareas = IntStream.range(0, 64)
                    .<Callable<String>>mapToObj(i -> provider::token).toList();
            Set<String> tokens = new HashSet<>();
            for (Future<String> resultado : pool.invokeAll(tareas)) {
                tokens.add(resultado.get());
            }

            assertThat(tokens).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Reloj que se adelanta a mano. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
