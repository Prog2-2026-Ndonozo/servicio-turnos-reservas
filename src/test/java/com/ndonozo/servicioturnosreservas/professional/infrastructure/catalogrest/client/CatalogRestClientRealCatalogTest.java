package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.client;

import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalSearchCriteria;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.config.CatalogRestConfig;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.ProfessionalResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.token.CatalogPrivateKeyReader;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.token.CatalogTokenIssuer;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.token.CatalogTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba MANUAL contra el catálogo real. Se omite salvo que se defina CATALOGO_REAL_URL, así que un ./mvnw test normal
 * no la corre. Requiere el catálogo levantado y la clave pública de turnos entregada (CATALOGO_JWT_PUBLIC_KEY_LOCATION):
 *
 *   CATALOGO_REAL_URL=http://localhost:8080 CATALOGO_PRIVATE_KEY_FILE=/ruta/turnos-private.pem \
 *       ./mvnw test -Dtest=CatalogRestClientRealCatalogTest
 *
 * No imprime tokens ni claves.
 */
@EnabledIfEnvironmentVariable(named = "CATALOGO_REAL_URL", matches = ".+")
class CatalogRestClientRealCatalogTest {

    private final CatalogRestClient client = new CatalogRestClient(
            CatalogRestConfig.restClient(System.getenv("CATALOGO_REAL_URL"), Duration.ofSeconds(2), Duration.ofSeconds(5)),
            new CatalogTokenProvider(new CatalogTokenIssuer(
                    CatalogPrivateKeyReader.read(Path.of(System.getenv("CATALOGO_PRIVATE_KEY_FILE"))),
                    "servicio-turnos", "servicio-catalogo", Clock.systemUTC()), Clock.systemUTC()));

    @Test
    void el_catalogo_real_acepta_el_token_y_responde_las_tres_operaciones() {
        var categorias = client.listCategories();
        List<ProfessionalResponse> profesionales = client.searchProfessionals(ProfessionalSearchCriteria.withoutFilters());
        System.out.println("Catálogo real: " + categorias.size() + " categorías, " + profesionales.size() + " profesionales");

        assertThat(categorias).isNotNull();
        if (!profesionales.isEmpty()) {
            long id = profesionales.getFirst().id();
            assertThat(client.findAgenda(id)).isPresent();
            assertThat(client.findAgenda(id).orElseThrow().professional().id()).isEqualTo(id);
        }
        assertThat(client.findAgenda(999_999_999L)).isEmpty();
    }
}
