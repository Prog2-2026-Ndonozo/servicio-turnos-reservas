package com.ndonozo.servicioturnosreservas.professional.domain.ports.out;

import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalCategory;

import java.util.List;

public interface ProfessionalCategoryPort {
    List<ProfessionalCategory> findAll();


}
