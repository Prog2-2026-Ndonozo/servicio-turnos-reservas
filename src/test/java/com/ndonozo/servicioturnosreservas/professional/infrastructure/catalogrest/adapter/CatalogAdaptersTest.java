package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.adapter;

import com.ndonozo.servicioturnosreservas.professional.application.exception.CatalogUnavailableException;
import com.ndonozo.servicioturnosreservas.professional.domain.model.Professional;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalAgenda;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalCategory;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalSearchCriteria;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.client.CatalogRestClient;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.ProfessionalAgendaResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.ProfessionalCategoryResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.ProfessionalResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.mapper.CatalogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogAdaptersTest {

    @Mock
    private CatalogRestClient client;

    private final CatalogMapper mapper = new CatalogMapper();

    @Test
    void la_busqueda_le_pasa_los_criterios_al_cliente_y_devuelve_el_dominio() {
        ProfessionalSearchCriteria criterios = new ProfessionalSearchCriteria(101L, "ana", true, LocalDate.of(2026, 11, 23));
        when(client.searchProfessionals(criterios))
                .thenReturn(List.of(new ProfessionalResponse(15L, 101L, "Ana", "Gomez", true)));

        List<Professional> resultado = new CatalogProfessionalSearchAdapter(client, mapper).search(criterios);

        assertThat(resultado).extracting(Professional::getId).containsExactly(15L);
        verify(client).searchProfessionals(criterios);
    }

    @Test
    void las_categorias_se_devuelven_como_dominio() {
        when(client.listCategories()).thenReturn(List.of(new ProfessionalCategoryResponse(101L, "Cardio", "", true)));

        List<ProfessionalCategory> resultado = new CatalogProfessionalCategoryAdapter(client, mapper).findAll();

        assertThat(resultado).extracting(ProfessionalCategory::getName).containsExactly("Cardio");
    }

    @Test
    void la_agenda_se_devuelve_como_dominio() {
        when(client.findAgenda(201)).thenReturn(Optional.of(new ProfessionalAgendaResponse(
                new ProfessionalResponse(201L, 101L, "Ana", "Torres", true), List.of())));

        Optional<ProfessionalAgenda> resultado = new CatalogProfessionalAgendaAdapter(client, mapper).findAgenda(201);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getProfessional().getId()).isEqualTo(201L);
    }

    @Test
    void un_profesional_inexistente_es_un_Optional_vacio() {
        when(client.findAgenda(999)).thenReturn(Optional.empty());

        assertThat(new CatalogProfessionalAgendaAdapter(client, mapper).findAgenda(999)).isEmpty();
    }

    @Test
    void las_fallas_del_cliente_llegan_sin_traducir_al_que_llama() {
        when(client.listCategories()).thenThrow(new CatalogUnavailableException("caído"));

        assertThatThrownBy(() -> new CatalogProfessionalCategoryAdapter(client, mapper).findAll())
                .isInstanceOf(CatalogUnavailableException.class);
    }
}
