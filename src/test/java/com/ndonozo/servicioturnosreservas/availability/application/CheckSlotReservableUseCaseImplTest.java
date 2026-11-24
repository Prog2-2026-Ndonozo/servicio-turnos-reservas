package com.ndonozo.servicioturnosreservas.availability.application;

import com.ndonozo.servicioturnosreservas.availability.application.usecases.CheckSlotReservableUseCaseImpl;
import com.ndonozo.servicioturnosreservas.availability.domain.model.SlotCheckResult;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Comprueba un turno contra la agenda vigente: existe en un horario habilitado, entra completo y no empezó. */
@ExtendWith(MockitoExtension.class)
class CheckSlotReservableUseCaseImplTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 11, 23);
    private static final LocalDate MARTES = LUNES.plusDays(1);
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Mock
    private GetProfessionalAgendaUseCase agendas;

    private CheckSlotReservableUseCaseImpl useCase(int horaActual) {
        return new CheckSlotReservableUseCaseImpl(agendas,
                Clock.fixed(LUNES.atTime(horaActual, 0).atZone(ZONA).toInstant(), ZONA));
    }

    private static WeeklySchedule horario(int minutosPorTurno, boolean habilitado) {
        return WeeklySchedule.builder().dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0)).slotDurationMinutes(minutosPorTurno).enabled(habilitado).build();
    }

    private void agendaConHorario(boolean profesionalHabilitado, WeeklySchedule... horarios) {
        when(agendas.getAgenda(15)).thenReturn(new ProfessionalAgendaResult.Found(ProfessionalAgenda.builder()
                .professional(Professional.builder().id(15L).enabled(profesionalHabilitado).build())
                .weeklySchedules(List.of(horarios)).build()));
    }

    @Test
    void un_turno_que_empieza_en_un_horario_habilitado_es_reservable() {
        agendaConHorario(true, horario(30, true));

        assertThat(useCase(8).checkSlot(15, LUNES, LocalTime.of(9, 0))).isInstanceOf(SlotCheckResult.Reservable.class);
        assertThat(useCase(8).checkSlot(15, LUNES, LocalTime.of(9, 30))).isInstanceOf(SlotCheckResult.Reservable.class);
    }

    @Test
    void una_hora_que_no_coincide_con_el_inicio_de_un_turno_no_es_valida() {
        agendaConHorario(true, horario(30, true));

        assertThat(useCase(8).checkSlot(15, LUNES, LocalTime.of(9, 15))).isInstanceOf(SlotCheckResult.InvalidSlot.class);
    }

    @Test
    void fuera_del_horario_no_es_valido() {
        agendaConHorario(true, horario(30, true));

        assertThat(useCase(8).checkSlot(15, LUNES, LocalTime.of(8, 30))).isInstanceOf(SlotCheckResult.InvalidSlot.class);
        // 10:00 es el fin del horario: ahí no empieza ningún turno
        assertThat(useCase(8).checkSlot(15, LUNES, LocalTime.of(10, 0))).isInstanceOf(SlotCheckResult.InvalidSlot.class);
    }

    @Test
    void un_dia_sin_horario_no_es_valido() {
        agendaConHorario(true, horario(30, true));

        assertThat(useCase(8).checkSlot(15, MARTES, LocalTime.of(9, 0))).isInstanceOf(SlotCheckResult.InvalidSlot.class);
    }

    @Test
    void un_turno_parcial_no_es_valido() {
        // de 9:00 a 10:00 con turnos de 45 minutos solo existe el de 9:00
        agendaConHorario(true, horario(45, true));

        assertThat(useCase(8).checkSlot(15, LUNES, LocalTime.of(9, 0))).isInstanceOf(SlotCheckResult.Reservable.class);
        assertThat(useCase(8).checkSlot(15, LUNES, LocalTime.of(9, 45))).isInstanceOf(SlotCheckResult.InvalidSlot.class);
    }

    @Test
    void un_horario_deshabilitado_se_ignora() {
        agendaConHorario(true, horario(30, false));

        assertThat(useCase(8).checkSlot(15, LUNES, LocalTime.of(9, 0))).isInstanceOf(SlotCheckResult.InvalidSlot.class);
    }

    @Test
    void un_turno_que_ya_empezo_no_es_valido() {
        agendaConHorario(true, horario(30, true));

        // a las 9:00 en punto el turno de las 9:00 ya empezó y el de las 9:30 todavía no
        assertThat(useCase(9).checkSlot(15, LUNES, LocalTime.of(9, 0))).isInstanceOf(SlotCheckResult.InvalidSlot.class);
        assertThat(useCase(9).checkSlot(15, LUNES, LocalTime.of(9, 30))).isInstanceOf(SlotCheckResult.Reservable.class);
    }

    @Test
    void un_profesional_deshabilitado_se_informa_aunque_el_turno_exista() {
        agendaConHorario(false, horario(30, true));

        assertThat(useCase(8).checkSlot(15, LUNES, LocalTime.of(9, 0))).isInstanceOf(SlotCheckResult.ProfessionalDisabled.class);
    }

    @Test
    void las_demas_respuestas_de_la_agenda_se_traducen_sin_perder_el_motivo() {
        when(agendas.getAgenda(1)).thenReturn(new ProfessionalAgendaResult.NotFound());
        when(agendas.getAgenda(2)).thenReturn(new ProfessionalAgendaResult.CatalogUnavailable());
        when(agendas.getAgenda(3)).thenReturn(new ProfessionalAgendaResult.CatalogFailed());

        assertThat(useCase(8).checkSlot(1, LUNES, LocalTime.of(9, 0))).isInstanceOf(SlotCheckResult.ProfessionalNotFound.class);
        assertThat(useCase(8).checkSlot(2, LUNES, LocalTime.of(9, 0))).isInstanceOf(SlotCheckResult.CatalogUnavailable.class);
        assertThat(useCase(8).checkSlot(3, LUNES, LocalTime.of(9, 0))).isInstanceOf(SlotCheckResult.CatalogFailed.class);
    }
}
