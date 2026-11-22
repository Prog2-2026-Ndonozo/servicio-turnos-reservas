package com.ndonozo.servicioturnosreservas.professional.domain.ports.in;

import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalCategory;

import java.util.List;

public interface ListProfessionalCategoriesUseCase {
    List<ProfessionalCategory> listCategories();
}
