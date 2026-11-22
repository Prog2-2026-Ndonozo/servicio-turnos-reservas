package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.adapter;

import com.ndonozo.servicioturnosreservas.professional.domain.model.Professional;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalSearchCriteria;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.out.ProfessionalSearchPort;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.client.CatalogRestClient;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.mapper.CatalogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CatalogProfessionalSearchAdapter implements ProfessionalSearchPort {

    private final CatalogRestClient client;
    private final CatalogMapper mapper;

    @Override
    public List<Professional> search(ProfessionalSearchCriteria criteria) {
        return client.searchProfessionals(criteria).stream().map(mapper::toDomain).toList();
    }
}

