package com.ndonozo.servicioturnosreservas.professional.application;

import com.ndonozo.servicioturnosreservas.professional.application.exception.CatalogIntegrationException;
import com.ndonozo.servicioturnosreservas.professional.application.exception.CatalogUnavailableException;
import com.ndonozo.servicioturnosreservas.professional.application.usecases.GetProfessionalAgendaUseCaseImpl;
import com.ndonozo.servicioturnosreservas.professional.domain.model.Professional;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalAgenda;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalAgendaResult;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.out.ProfessionalAgendaPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Otros módulos no ven las excepciones de professional: las fallas del catálogo salen como valor del resultado. */
@ExtendWith(MockitoExtension.class)
class GetProfessionalAgendaUseCaseImplTest {

    @Mock
    private ProfessionalAgendaPort agendaPort;

    @Test
    void una_agenda_existente_sale_como_Found() {
        ProfessionalAgenda agenda = ProfessionalAgenda.builder()
                .professional(Professional.builder().id(15L).build()).weeklySchedules(List.of()).build();
        when(agendaPort.findAgenda(15)).thenReturn(Optional.of(agenda));

        assertThat(new GetProfessionalAgendaUseCaseImpl(agendaPort).getAgenda(15))
                .isEqualTo(new ProfessionalAgendaResult.Found(agenda));
    }

    @Test
    void un_profesional_inexistente_sale_como_NotFound() {
        when(agendaPort.findAgenda(15)).thenReturn(Optional.empty());

        assertThat(new GetProfessionalAgendaUseCaseImpl(agendaPort).getAgenda(15))
                .isInstanceOf(ProfessionalAgendaResult.NotFound.class);
    }

    @Test
    void un_catalogo_caido_sale_como_CatalogUnavailable() {
        when(agendaPort.findAgenda(15)).thenThrow(new CatalogUnavailableException("caído"));

        assertThat(new GetProfessionalAgendaUseCaseImpl(agendaPort).getAgenda(15))
                .isInstanceOf(ProfessionalAgendaResult.CatalogUnavailable.class);
    }

    @Test
    void un_error_de_integracion_sale_como_CatalogFailed() {
        when(agendaPort.findAgenda(15)).thenThrow(new CatalogIntegrationException("403"));

        assertThat(new GetProfessionalAgendaUseCaseImpl(agendaPort).getAgenda(15))
                .isInstanceOf(ProfessionalAgendaResult.CatalogFailed.class);
    }
}
