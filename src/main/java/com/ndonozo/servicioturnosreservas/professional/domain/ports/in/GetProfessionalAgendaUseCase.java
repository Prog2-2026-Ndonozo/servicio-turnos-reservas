package com.ndonozo.servicioturnosreservas.professional.domain.ports.in;

import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalAgendaResult;

public interface GetProfessionalAgendaUseCase {
    ProfessionalAgendaResult getAgenda(long professionalId);
}
