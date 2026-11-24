package com.ndonozo.servicioturnosreservas.availability.application;

import com.ndonozo.servicioturnosreservas.availability.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.availability.application.usecases.GetAvailabilityUseCaseImpl;
import com.ndonozo.servicioturnosreservas.availability.domain.model.Availability;
import com.ndonozo.servicioturnosreservas.availability.domain.model.AvailabilityResult;
import com.ndonozo.servicioturnosreservas.availability.domain.model.AvailabilitySlot;
import com.ndonozo.servicioturnosreservas.availability.domain.model.Occupancy;
import com.ndonozo.servicioturnosreservas.availability.domain.ports.out.OccupancyPort;
import com.ndonozo.servicioturnosreservas.professional.domain.model.Professional;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalAgenda;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalAgendaResult;
import com.ndonozo.servicioturnosreservas.professional.domain.model.WeeklySchedule;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.in.GetProfessionalAgendaUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAvailabilityUseCaseImplTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 11, 23);
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Mock
    private GetProfessionalAgendaUseCase agendas;

    @Mock
    private OccupancyPort occupancyPort;

    private GetAvailabilityUseCaseImpl useCase(Clock reloj) {
        return new GetAvailabilityUseCaseImpl(agendas, occupancyPort, reloj);
    }

    // 08:00 del lunes 23/11/2026 en Buenos Aires
    private static Clock lunesA(int hora) {
        return Clock.fixed(LUNES.atTime(hora, 0).atZone(ZONA).toInstant(), ZONA);
    }

    private static ProfessionalAgenda agenda(boolean habilitado) {
        return ProfessionalAgenda.builder()
                .professional(Professional.builder().id(15L).enabled(habilitado).build())
                .weeklySchedules(List.of(WeeklySchedule.builder().dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(10, 0)).slotDurationMinutes(30).enabled(true).build()))
                .build();
    }

    @Test
    void combina_la_agenda_del_catalogo_con_las_ocupaciones_de_la_catedra() {
        when(agendas.getAgenda(15)).thenReturn(new ProfessionalAgendaResult.Found(agenda(true)));
        when(occupancyPort.findOccupancies(15, LUNES, LUNES)).thenReturn(List.of(new Occupancy(LUNES, LocalTime.of(9, 0))));

        AvailabilityResult resultado = useCase(lunesA(8)).getAvailability(15, LUNES, LUNES);

        assertThat(resultado).isEqualTo(new AvailabilityResult.Found(new Availability(15, LUNES, LUNES,
                List.of(new AvailabilitySlot(LUNES, LocalTime.of(9, 30), LocalTime.of(10, 0))))));
    }

    @Test
    void usa_la_hora_del_reloj_de_atencion_para_descartar_lo_que_ya_empezo() {
        when(agendas.getAgenda(15)).thenReturn(new ProfessionalAgendaResult.Found(agenda(true)));
        when(occupancyPort.findOccupancies(15, LUNES, LUNES)).thenReturn(List.of());

        AvailabilityResult resultado = useCase(lunesA(9)).getAvailability(15, LUNES, LUNES);

        assertThat(((AvailabilityResult.Found) resultado).availability().slots())
                .containsExactly(new AvailabilitySlot(LUNES, LocalTime.of(9, 30), LocalTime.of(10, 0)));
    }

    @Test
    void la_hora_actual_se_toma_en_la_zona_del_reloj_y_no_en_la_del_servidor() {
        when(agendas.getAgenda(15)).thenReturn(new ProfessionalAgendaResult.Found(agenda(true)));
        when(occupancyPort.findOccupancies(15, LUNES, LUNES)).thenReturn(List.of());
        // 12:00 UTC es 09:00 en Buenos Aires: el turno de las 9:00 ya empezó
        Clock utcMedioDia = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZONA);

        AvailabilityResult resultado = useCase(utcMedioDia).getAvailability(15, LUNES, LUNES);

        assertThat(((AvailabilityResult.Found) resultado).availability().slots()).hasSize(1);
    }

    @Test
    void un_profesional_deshabilitado_no_ofrece_turnos_y_no_se_consulta_a_la_catedra() {
        when(agendas.getAgenda(15)).thenReturn(new ProfessionalAgendaResult.Found(agenda(false)));

        AvailabilityResult resultado = useCase(lunesA(8)).getAvailability(15, LUNES, LUNES);

        assertThat(resultado).isEqualTo(new AvailabilityResult.Found(new Availability(15, LUNES, LUNES, List.of())));
        verifyNoInteractions(occupancyPort);
    }

    @Test
    void un_rango_invertido_se_rechaza_sin_llamar_a_nadie() {
        AvailabilityResult resultado = useCase(lunesA(8)).getAvailability(15, LUNES.plusDays(1), LUNES);

        assertThat(resultado).isInstanceOf(AvailabilityResult.InvalidRange.class);
        verifyNoInteractions(agendas, occupancyPort);
    }

    @Test
    void el_rango_maximo_son_31_dias_inclusive() {
        when(agendas.getAgenda(15)).thenReturn(new ProfessionalAgendaResult.Found(agenda(true)));
        when(occupancyPort.findOccupancies(15, LUNES, LUNES.plusDays(30))).thenReturn(List.of());

        assertThat(useCase(lunesA(8)).getAvailability(15, LUNES, LUNES.plusDays(30)))
                .isInstanceOf(AvailabilityResult.Found.class);
        assertThat(useCase(lunesA(8)).getAvailability(15, LUNES, LUNES.plusDays(31)))
                .isInstanceOf(AvailabilityResult.InvalidRange.class);
    }

    @Test
    void un_dia_solo_es_un_rango_valido() {
        when(agendas.getAgenda(15)).thenReturn(new ProfessionalAgendaResult.Found(agenda(true)));
        when(occupancyPort.findOccupancies(15, LUNES, LUNES)).thenReturn(List.of());

        assertThat(useCase(lunesA(8)).getAvailability(15, LUNES, LUNES)).isInstanceOf(AvailabilityResult.Found.class);
    }

    @Test
    void un_profesional_inexistente_no_consulta_a_la_catedra() {
        when(agendas.getAgenda(15)).thenReturn(new ProfessionalAgendaResult.NotFound());

        assertThat(useCase(lunesA(8)).getAvailability(15, LUNES, LUNES)).isInstanceOf(AvailabilityResult.ProfessionalNotFound.class);
        verifyNoInteractions(occupancyPort);
    }

    @Test
    void las_fallas_del_catalogo_salen_como_resultado() {
        when(agendas.getAgenda(1)).thenReturn(new ProfessionalAgendaResult.CatalogUnavailable());
        when(agendas.getAgenda(2)).thenReturn(new ProfessionalAgendaResult.CatalogFailed());

        assertThat(useCase(lunesA(8)).getAvailability(1, LUNES, LUNES)).isInstanceOf(AvailabilityResult.CatalogUnavailable.class);
        assertThat(useCase(lunesA(8)).getAvailability(2, LUNES, LUNES)).isInstanceOf(AvailabilityResult.CatalogFailed.class);
        verifyNoInteractions(occupancyPort);
    }

    @Test
    void si_la_catedra_falla_la_falla_llega_intacta_sin_inventar_disponibilidad() {
        when(agendas.getAgenda(15)).thenReturn(new ProfessionalAgendaResult.Found(agenda(true)));
        when(occupancyPort.findOccupancies(15, LUNES, LUNES)).thenThrow(new CatedraUnavailableException("caída"));

        assertThatThrownBy(() -> useCase(lunesA(8)).getAvailability(15, LUNES, LUNES))
                .isInstanceOf(CatedraUnavailableException.class);
    }

    @Test
    void cada_consulta_vuelve_a_preguntar_a_los_dos_sistemas() {
        when(agendas.getAgenda(15)).thenReturn(new ProfessionalAgendaResult.Found(agenda(true)));
        when(occupancyPort.findOccupancies(15, LUNES, LUNES)).thenReturn(List.of());

        useCase(lunesA(8)).getAvailability(15, LUNES, LUNES);
        useCase(lunesA(8)).getAvailability(15, LUNES, LUNES);

        verify(agendas, org.mockito.Mockito.times(2)).getAgenda(15);
        verify(occupancyPort, org.mockito.Mockito.times(2)).findOccupancies(15, LUNES, LUNES);
    }
}
