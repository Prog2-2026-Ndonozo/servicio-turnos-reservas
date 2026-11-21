package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.mapper;

import com.ndonozo.servicioturnosreservas.professional.domain.model.Professional;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalAgenda;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalCategory;
import com.ndonozo.servicioturnosreservas.professional.domain.model.WeeklySchedule;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.ProfessionalAgendaResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.ProfessionalCategoryResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.ProfessionalResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.WeeklyScheduleResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CatalogMapper {
    public Professional toDomain(ProfessionalResponse response ){
        if (response == null){
            return null;
        }
        return Professional.builder().
                id(response.id())
                .categoryId(response.categoryId())
                .firstName(response.firstName())
                .lastName(response.lastName())
                .enabled(response.enabled())
                .build();
    }
    public ProfessionalCategory toDomain(ProfessionalCategoryResponse response){
        if (response == null){
            return null;

        }
        return ProfessionalCategory.builder().
                id(response.id())
                .name(response.name())
                .description(response.description())
                .enabled(response.enabled()).build();}

    public WeeklySchedule toDomain(WeeklyScheduleResponse response){
        if (response == null){
            return null;

        }
        return WeeklySchedule.builder().
                id(response.id())
                .dayOfWeek(response.dayOfWeek())
                .startTime(response.startTime())
                .endTime(response.endTime())
                .slotDurationMinutes(response.slotDurationMinutes()).
                enabled(response.enabled())
                .build();

    }
    public ProfessionalAgenda toDomain(ProfessionalAgendaResponse response){
        if (response == null){
            return null;
        }
        List<WeeklyScheduleResponse> schedules = response.weeklySchedules() == null ? List.of() : response.weeklySchedules();
        return ProfessionalAgenda.builder()
                .professional(toDomain(response.professional()))
                .weeklySchedules(schedules.stream().map(this::toDomain).toList())
                .build();
    }
}
