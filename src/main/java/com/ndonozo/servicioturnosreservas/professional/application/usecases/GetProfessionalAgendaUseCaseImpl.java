package com.ndonozo.servicioturnosreservas.professional.application.usecases;


import com.ndonozo.servicioturnosreservas.professional.application.exception.CatalogIntegrationException;
import com.ndonozo.servicioturnosreservas.professional.application.exception.CatalogUnavailableException;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalAgendaResult;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.in.GetProfessionalAgendaUseCase;
import com.ndonozo.servicioturnosreservas.professional.domain.ports.out.ProfessionalAgendaPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
/**
 * Obtiene la agenda (profesional + horarios semanales) desde el catálogo. Lo consumen otros módulos
 * (availability y, a través de él, reservation) por el puerto de entrada: no tiene endpoint propio.
 * <p>
 * Lo más importante: convierte las excepciones de infraestructura (catálogo caído o con error) en resultados,
 * así quien lo usa recibe un valor y no tiene que atrapar excepciones.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetProfessionalAgendaUseCaseImpl implements GetProfessionalAgendaUseCase {
    // Puerto de salida: el adaptador pregunta al servicio del catálogo por HTTP con el JWT técnico.
    private final ProfessionalAgendaPort agendaPort;

    @Override
    public ProfessionalAgendaResult getAgenda(long professionalId){
        try{
            // Si el catálogo devuelve la agenda: Found. Si el profesional no existe (Optional vacío): NotFound.
            return agendaPort.findAgenda(professionalId).<ProfessionalAgendaResult>map(ProfessionalAgendaResult.Found::new).
                    orElseGet(ProfessionalAgendaResult.NotFound::new);

        // Catálogo caído, sin respuesta o con 5xx: resultado CatalogUnavailable (termina en 503).
        }catch(CatalogUnavailableException e){
            log.warn("El catalogo no esta disponible: {}", e.getMessage());
            return new ProfessionalAgendaResult.CatalogUnavailable();
        }
        // Error de integración (por ejemplo, un 401 o 403 del catálogo): resultado CatalogFailed (termina en 502).
        catch(CatalogIntegrationException e){
            log.warn("Fallo la integracion con el catalogo: {}", e.getMessage());
            return new ProfessionalAgendaResult.CatalogFailed();
       }

    }


}
