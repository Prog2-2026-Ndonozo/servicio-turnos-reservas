package com.ndonozo.servicioturnosreservas.professional.domain.model;

import java.time.LocalDate;

public record ProfessionalSearchCriteria(Long categoryId, String name, Boolean enabled, LocalDate availableOn) {

    public static ProfessionalSearchCriteria withoutFilters(){
        return new ProfessionalSearchCriteria(null,null,null,null);
    }
}
