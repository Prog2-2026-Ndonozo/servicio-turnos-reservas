package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.mapper;

import com.ndonozo.servicioturnosreservas.professional.domain.model.Professional;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalAgenda;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalCategory;
import com.ndonozo.servicioturnosreservas.professional.domain.model.WeeklySchedule;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.ProfessionalAgendaResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.ProfessionalCategoryResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.ProfessionalResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.WeeklyScheduleResponse;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogMapperTest {

    private final CatalogMapper mapper = new CatalogMapper();

    @Test
    void mapea_todos_los_campos_del_profesional() {
        Professional professional = mapper.toDomain(new ProfessionalResponse(15L, 101L, "Ana", "Gomez", true));

        assertThat(professional).usingRecursiveComparison().isEqualTo(
                Professional.builder().id(15L).categoryId(101L).firstName("Ana").lastName("Gomez").enabled(true).build());
    }

    @Test
    void mapea_todos_los_campos_de_la_categoria() {
        ProfessionalCategory category =
                mapper.toDomain(new ProfessionalCategoryResponse(101L, "Cardiologia", "Atencion", false));

        assertThat(category).usingRecursiveComparison().isEqualTo(ProfessionalCategory.builder()
                .id(101L).name("Cardiologia").description("Atencion").enabled(false).build());
    }

    @Test
    void mapea_todos_los_campos_del_horario() {
        WeeklySchedule schedule = mapper.toDomain(new WeeklyScheduleResponse(
                301L, DayOfWeek.FRIDAY, LocalTime.of(9, 30), LocalTime.of(13, 0), 45, true));

        assertThat(schedule).usingRecursiveComparison().isEqualTo(WeeklySchedule.builder().id(301L)
                .dayOfWeek(DayOfWeek.FRIDAY).startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(13, 0))
                .slotDurationMinutes(45).enabled(true).build());
    }

    @Test
    void mapea_la_agenda_completa_conservando_los_horarios_deshabilitados_y_su_orden() {
        ProfessionalAgenda agenda = mapper.toDomain(new ProfessionalAgendaResponse(
                new ProfessionalResponse(201L, 101L, "Ana", "Torres", true),
                List.of(new WeeklyScheduleResponse(1L, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), 30, true),
                        new WeeklyScheduleResponse(2L, DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), 30, false))));

        assertThat(agenda.getProfessional().getId()).isEqualTo(201L);
        assertThat(agenda.getWeeklySchedules()).extracting(WeeklySchedule::getId).containsExactly(1L, 2L);
        assertThat(agenda.getWeeklySchedules()).extracting(WeeklySchedule::isEnabled).containsExactly(true, false);
    }

    @Test
    void una_agenda_sin_lista_de_horarios_queda_con_lista_vacia() {
        ProfessionalAgenda agenda = mapper.toDomain(new ProfessionalAgendaResponse(
                new ProfessionalResponse(201L, 101L, "Ana", "Torres", true), null));

        assertThat(agenda.getWeeklySchedules()).isEmpty();
    }

    @Test
    void devuelve_null_ante_null() {
        assertThat(mapper.toDomain((ProfessionalResponse) null)).isNull();
        assertThat(mapper.toDomain((ProfessionalCategoryResponse) null)).isNull();
        assertThat(mapper.toDomain((WeeklyScheduleResponse) null)).isNull();
        assertThat(mapper.toDomain((ProfessionalAgendaResponse) null)).isNull();
    }
}
