package com.ndonozo.servicioturnosreservas.professional.application.usecases;

import com.ndonozo.servicioturnosreservas.professional.domain.model.Professional;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalSearchCriteria;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.in.SearchProfessionalUseCase;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.out.ProfessionalSearchPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Busca profesionales. Como las categorías, no tiene reglas propias: reenvía los criterios de búsqueda al catálogo
 * por el puerto de salida y devuelve lo que este responde.
 */
@Component
@RequiredArgsConstructor
public class SearchProfessionalUseCaseImpl implements SearchProfessionalUseCase {
    // Puerto de salida: el adaptador llama al catálogo por HTTP con el JWT técnico.
    private final ProfessionalSearchPort searchPort;

    @Override
    public List<Professional> searchProfessionals(ProfessionalSearchCriteria criteria){
        return searchPort.search(criteria);
    }
}
