package com.ndonozo.servicioturnosreservas.professional.application.usecases;

import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalCategory;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalSearchCriteria;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.in.ListProfessionalCategoriesUseCase;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.out.ProfessionalCategoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListProfessionalCategoriesUseCaseImpl implements ListProfessionalCategoriesUseCase {
    private final ProfessionalCategoryPort categoryPort;

    @Override
    public List<ProfessionalCategory> listCategories(){
        return categoryPort.findAll();
    }

}
