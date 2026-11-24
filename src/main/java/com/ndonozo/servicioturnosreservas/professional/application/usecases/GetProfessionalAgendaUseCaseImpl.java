package com.ndonozo.servicioturnosreservas.professional.application.usecases;


import com.ndonozo.servicioturnosreservas.professional.application.exception.CatalogIntegrationException;
import com.ndonozo.servicioturnosreservas.professional.application.exception.CatalogUnavailableException;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalAgendaResult;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.in.GetProfessionalAgendaUseCase;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.out.ProfessionalAgendaPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
@Slf4j
@Component
@RequiredArgsConstructor
public class GetProfessionalAgendaUseCaseImpl implements GetProfessionalAgendaUseCase {
    private final ProfessionalAgendaPort agendaPort;

    @Override
    public ProfessionalAgendaResult getAgenda(long professionalId){
        try{
            return agendaPort.findAgenda(professionalId).<ProfessionalAgendaResult>map(ProfessionalAgendaResult.Found::new).
                    orElseGet(ProfessionalAgendaResult.NotFound::new);

        }catch(CatalogUnavailableException e){
            log.warn("El catalogo no esta disponible: {}", e.getMessage());
            return new ProfessionalAgendaResult.CatalogUnavailable();
        }
        catch(CatalogIntegrationException e){
            log.warn("Fallo la integracion con el catalogo: {}", e.getMessage());
            return new ProfessionalAgendaResult.CatalogFailed();
       }

    }


}
