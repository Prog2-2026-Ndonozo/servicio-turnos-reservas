package com.ndonozo.servicioturnosreservas.professional.domain.ports.out;

import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalAgenda;

import java.util.Optional;

public interface ProfessionalAgendaPort {
    Optional<ProfessionalAgenda> findAgenda(long ProfessionalId);
}
