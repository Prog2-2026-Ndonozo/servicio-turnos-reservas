package com.ndonozo.servicioturnosreservas.professional.domain.ports.out;

import com.ndonozo.servicioturnosreservas.professional.domain.model.Professional;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalSearchCriteria;

import java.util.List;

public interface ProfessionalSearchPort {
    List<Professional> search(ProfessionalSearchCriteria criteria);

}
