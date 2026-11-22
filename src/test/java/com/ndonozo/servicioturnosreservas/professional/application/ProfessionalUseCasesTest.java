package com.ndonozo.servicioturnosreservas.professional.application;

import com.ndonozo.servicioturnosreservas.professional.application.exception.CatalogUnavailableException;
import com.ndonozo.servicioturnosreservas.professional.application.service.ProfessionalService;
import com.ndonozo.servicioturnosreservas.professional.application.usecases.ListProfessionalCategoriesUseCaseImpl;
import com.ndonozo.servicioturnosreservas.professional.application.usecases.SearchProfessionalUseCaseImpl;
import com.ndonozo.servicioturnosreservas.professional.domain.model.Professional;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalCategory;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalSearchCriteria;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.out.ProfessionalCategoryPort;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.out.ProfessionalSearchPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfessionalUseCasesTest {

    @Mock
    private ProfessionalSearchPort searchPort;

    @Mock
    private ProfessionalCategoryPort categoryPort;

    private ProfessionalService service() {
        return new ProfessionalService(new SearchProfessionalUseCaseImpl(searchPort),
                new ListProfessionalCategoriesUseCaseImpl(categoryPort));
    }

    @Test
    void la_busqueda_le_pasa_al_catalogo_exactamente_los_criterios_recibidos() {
        ProfessionalSearchCriteria criterios = new ProfessionalSearchCriteria(101L, "ana", true, LocalDate.of(2026, 11, 23));
        when(searchPort.search(criterios)).thenReturn(List.of());

        service().search(criterios);

        verify(searchPort).search(criterios);
        verifyNoMoreInteractions(searchPort);
    }

    @Test
    void la_busqueda_devuelve_lo_que_responde_el_catalogo_en_su_mismo_orden_y_sin_filtrar() {
        Professional habilitado = Professional.builder().id(2L).enabled(true).build();
        Professional deshabilitado = Professional.builder().id(1L).enabled(false).build();
        ProfessionalSearchCriteria sinFiltros = ProfessionalSearchCriteria.withoutFilters();
        when(searchPort.search(sinFiltros)).thenReturn(List.of(habilitado, deshabilitado));

        assertThat(service().search(sinFiltros)).containsExactly(habilitado, deshabilitado);
    }

    @Test
    void las_categorias_salen_del_catalogo_en_su_orden() {
        ProfessionalCategory a = ProfessionalCategory.builder().id(2L).name("A").build();
        ProfessionalCategory b = ProfessionalCategory.builder().id(1L).name("B").enabled(false).build();
        when(categoryPort.findAll()).thenReturn(List.of(a, b));

        assertThat(service().categories()).containsExactly(a, b);
    }

    @Test
    void si_el_catalogo_falla_la_falla_llega_intacta_sin_ocultarla_con_datos_viejos() {
        when(categoryPort.findAll()).thenThrow(new CatalogUnavailableException("caído"));

        assertThatThrownBy(() -> service().categories()).isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void cada_consulta_vuelve_a_preguntarle_al_catalogo_no_se_guarda_nada() {
        when(categoryPort.findAll()).thenReturn(List.of());

        service().categories();
        service().categories();

        org.mockito.Mockito.verify(categoryPort, org.mockito.Mockito.times(2)).findAll();
    }
}
