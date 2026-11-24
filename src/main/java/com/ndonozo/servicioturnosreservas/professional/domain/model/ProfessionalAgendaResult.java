package com.ndonozo.servicioturnosreservas.professional.domain.model;

public sealed interface ProfessionalAgendaResult {
    record Found(ProfessionalAgenda agenda) implements ProfessionalAgendaResult {}
    record NotFound() implements ProfessionalAgendaResult {}
    record CatalogUnavailable() implements ProfessionalAgendaResult{}
    record CatalogFailed() implements ProfessionalAgendaResult{}
}
