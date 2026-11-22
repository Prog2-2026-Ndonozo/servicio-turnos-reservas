package com.ndonozo.servicioturnosreservas.professional.application.service;

import com.ndonozo.servicioturnosreservas.professional.domain.model.Professional;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalCategory;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalSearchCriteria;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.in.ListProfessionalCategoriesUseCase;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.in.SearchProfessionalUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfessionalService {
    private final SearchProfessionalUseCase searchProfessionals;
    private final ListProfessionalCategoriesUseCase listCategories;

    public List<Professional> search(ProfessionalSearchCriteria criteria){
        return searchProfessionals.searchProfessionals(criteria);

    }

    public List<ProfessionalCategory> categories(){
        return listCategories.listCategories();
    }
}
