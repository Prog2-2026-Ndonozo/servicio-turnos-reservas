package com.ndonozo.servicioturnosreservas.professional.domain.ports.in;

import com.ndonozo.servicioturnosreservas.professional.domain.model.Professional;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalSearchCriteria;

import java.util.List;

public interface SearchProfessionalUseCase {
    List<Professional> searchProfessionals(ProfessionalSearchCriteria criteria);
}
