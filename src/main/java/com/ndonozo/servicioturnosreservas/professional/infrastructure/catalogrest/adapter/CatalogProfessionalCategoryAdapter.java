package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.adapter;

import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalCategory;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.out.ProfessionalCategoryPort;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.client.CatalogRestClient;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.mapper.CatalogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CatalogProfessionalCategoryAdapter implements ProfessionalCategoryPort {

    private final CatalogRestClient client;
    private final CatalogMapper mapper;

    @Override
    public List<ProfessionalCategory> findAll() {
        return client.listCategories().stream().map(mapper::toDomain).toList();
    }
}
