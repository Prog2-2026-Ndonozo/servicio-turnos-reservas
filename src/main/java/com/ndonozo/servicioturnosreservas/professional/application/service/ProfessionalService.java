package com.ndonozo.servicioturnosreservas.professional.application.service;

import com.ndonozo.servicioturnosreservas.professional.domain.model.Professional;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalCategory;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalSearchCriteria;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.in.ListProfessionalCategoriesUseCase;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.in.SearchProfessionalUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fachada del módulo professional (el controller la llama para buscar profesionales y listar categorías).
 * Solo envuelve dos casos de uso: no traduce resultados porque esos casos de uso devuelven listas directas.
 * El tercero (GetProfessionalAgendaUseCase) no está acá: no se expone por HTTP, lo usa availability por su puerto.
 */
@Service
@RequiredArgsConstructor
public class ProfessionalService {
    private final SearchProfessionalUseCase searchProfessionals;
    private final ListProfessionalCategoriesUseCase listCategories;

    // Búsqueda de profesionales en el catálogo, con los criterios que llegan del cliente.
    public List<Professional> search(ProfessionalSearchCriteria criteria){
        return searchProfessionals.searchProfessionals(criteria);

    }

    // Categorías de profesionales del catálogo.
    public List<ProfessionalCategory> categories(){
        return listCategories.listCategories();
    }
}
