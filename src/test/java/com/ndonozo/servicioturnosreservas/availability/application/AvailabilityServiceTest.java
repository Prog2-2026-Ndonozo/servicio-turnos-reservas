package com.ndonozo.servicioturnosreservas.availability.application;

import com.ndonozo.servicioturnosreservas.availability.application.exception.AgendaIntegrationException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.AgendaUnavailableException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.InvalidDateRangeException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.ProfessionalNotFoundException;
import com.ndonozo.servicioturnosreservas.availability.application.service.AvailabilityService;
import com.ndonozo.servicioturnosreservas.availability.domain.model.Availability;
import com.ndonozo.servicioturnosreservas.availability.domain.model.AvailabilityResult;
import com.ndonozo.servicioturnosreservas.availability.domain.ports.in.GetAvailabilityUseCase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La fachada traduce cada resultado: el valor sale tal cual y cada falla se vuelve su excepción. */
class AvailabilityServiceTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 23);

    private static AvailabilityService conResultado(AvailabilityResult resultado) {
        GetAvailabilityUseCase useCase = (professionalId, from, to) -> resultado;
        return new AvailabilityService(useCase);
    }

    @Test
    void devuelve_la_disponibilidad_encontrada() {
        Availability disponibilidad = new Availability(15, DIA, DIA, List.of());

        assertThat(conResultado(new AvailabilityResult.Found(disponibilidad)).getAvailability(15, DIA, DIA))
                .isSameAs(disponibilidad);
    }

    @Test
    void cada_falla_se_vuelve_su_excepcion() {
        assertThatThrownBy(() -> conResultado(new AvailabilityResult.InvalidRange("mal")).getAvailability(15, DIA, DIA))
                .isInstanceOf(InvalidDateRangeException.class).hasMessage("mal");
        assertThatThrownBy(() -> conResultado(new AvailabilityResult.ProfessionalNotFound()).getAvailability(15, DIA, DIA))
                .isInstanceOf(ProfessionalNotFoundException.class).hasMessageContaining("15");
        assertThatThrownBy(() -> conResultado(new AvailabilityResult.CatalogUnavailable()).getAvailability(15, DIA, DIA))
                .isInstanceOf(AgendaUnavailableException.class);
        assertThatThrownBy(() -> conResultado(new AvailabilityResult.CatalogFailed()).getAvailability(15, DIA, DIA))
                .isInstanceOf(AgendaIntegrationException.class);
    }
}
