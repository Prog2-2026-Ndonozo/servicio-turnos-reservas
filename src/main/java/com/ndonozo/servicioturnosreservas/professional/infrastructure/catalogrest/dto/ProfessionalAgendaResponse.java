package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfessionalAgendaResponse(
        ProfessionalResponse professional,
        List<WeeklyScheduleResponse> weeklySchedules
) {}