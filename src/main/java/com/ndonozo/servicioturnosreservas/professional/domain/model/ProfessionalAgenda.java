package com.ndonozo.servicioturnosreservas.professional.domain.model;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfessionalAgenda {
    private Professional professional;
    List<WeeklySchedule> weeklySchedules;

}
