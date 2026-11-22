package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.adapter;

import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalAgenda;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.out.ProfessionalAgendaPort;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.client.CatalogRestClient;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.mapper.CatalogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CatalogProfessionalAgendaAdapter implements ProfessionalAgendaPort {

    private final CatalogRestClient client;
    private final CatalogMapper mapper;

    @Override
    public Optional<ProfessionalAgenda> findAgenda(long professionalId) {
        return client.findAgenda(professionalId).map(mapper::toDomain);
    }


}
