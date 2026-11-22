package com.ndonozo.servicioturnosreservas.professional.application.usecases;

import com.ndonozo.servicioturnosreservas.professional.domain.model.Professional;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalSearchCriteria;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.in.SearchProfessionalUseCase;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.out.ProfessionalSearchPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SearchProfessionalUseCaseImpl implements SearchProfessionalUseCase {
    private final ProfessionalSearchPort searchPort;

    @Override
    public List<Professional> searchProfessionals(ProfessionalSearchCriteria criteria){
        return searchPort.search(criteria);
    }
}
