package com.ndonozo.servicioturnosreservas.availability.infrastructure.catedrarest.client;

import com.ndonozo.servicioturnosreservas.availability.application.exception.CatedraIntegrationException;
import com.ndonozo.servicioturnosreservas.availability.application.exception.CatedraUnavailableException;
import com.ndonozo.servicioturnosreservas.availability.infrastructure.catedrarest.dto.OccupanciesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class CatedraRestClient {

    // Bean compartido de config/: ya lleva la URL base, los tiempos de espera y el JWT tecnico

    private final RestClient restClient;

    public OccupanciesResponse findOccupancies(long professionalId, LocalDate from, LocalDate to){
        try{
            OccupanciesResponse body = restClient.get().uri(uri-> uri.path("/api/appointment-occupancies")
                    .queryParam("professionalId", professionalId)
                    .queryParam("from", from)
                    .queryParam("to", to)
                    .build())
                    .retrieve()
                    .body(OccupanciesResponse.class);
            if (body==null || body.occupancies() == null){
                throw new CatedraIntegrationException("La catedra respondio sin la lista de ocupaciones");

            }
            return body;

        } catch (HttpClientErrorException e){ // 400, 401 y 403: el JWT tecnico no se renueva y el rango ya se valido, asi toma con un error de integracion
            throw new CatedraIntegrationException("La catedra respondio HTTP " + e.getStatusCode().value() + ": el pedido de turnos no cumple su contrato o el JWT tecnico no es valido",e );

        } catch (HttpServerErrorException e){
            throw new CatedraUnavailableException("La catedra respondio HTTP " + e.getStatusCode().value(), e);

        } catch (ResourceAccessException e){
            throw new CatedraUnavailableException("No se pudo hablar con la catedra: " + e.getMessage(), e);

        } catch (RestClientException e){
            throw new CatedraIntegrationException("No se pudo leer la respuesta de la catedra", e);
        }


    }
}
