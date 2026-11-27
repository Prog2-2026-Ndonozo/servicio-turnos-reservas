package com.ndonozo.servicioturnosreservas.professional.application.usecases;

import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalCategory;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalSearchCriteria;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.in.ListProfessionalCategoriesUseCase;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.out.ProfessionalCategoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Lista las categorías de profesionales. No tiene reglas propias: pide la lista al catálogo por el puerto de salida.
 * El catálogo es el dueño de estos datos; turnos no los guarda ni los cachea.
 */
@Component
@RequiredArgsConstructor
public class ListProfessionalCategoriesUseCaseImpl implements ListProfessionalCategoriesUseCase {
    // Puerto de salida: el adaptador llama al catálogo por HTTP.
    private final ProfessionalCategoryPort categoryPort;

    @Override
    public List<ProfessionalCategory> listCategories(){
        return categoryPort.findAll();
    }

}
