package com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.client;

import com.ndonozo.servicioturnosreservas.professional.application.exception.CatalogIntegrationException;
import com.ndonozo.servicioturnosreservas.professional.application.exception.CatalogUnavailableException;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalSearchCriteria;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.ProfessionalAgendaResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.ProfessionalCategoryResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.dto.ProfessionalResponse;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.catalogrest.token.CatalogTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.*;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
public class CatalogRestClient {

    private static final String PROFESSIONAL_NOT_FOUND = "PROFESSIONAL_NOT_FOUND";

    private final RestClient restClient;
    private final CatalogTokenProvider tokens;

    public CatalogRestClient(RestClient restClient, CatalogTokenProvider tokens) {
        this.restClient = restClient;
        this.tokens = tokens;
    }

    public List<ProfessionalResponse> searchProfessionals(ProfessionalSearchCriteria criteria) {
        return fetch(uri -> searchUri(uri, criteria), new ParameterizedTypeReference<>() {}, false);
    }

    public List<ProfessionalCategoryResponse> listCategories() {
        return fetch(uri -> uri.path("/api/professional-categories").build(), new ParameterizedTypeReference<>() {}, false);
    }

    public Optional<ProfessionalAgendaResponse> findAgenda(long professionalId) {
        try {
            return Optional.of(fetch(uri -> uri.path("/api/professionals/{id}/agenda").build(professionalId),
                    new ParameterizedTypeReference<ProfessionalAgendaResponse>() {}, true));
        } catch (ProfessionalNotFound e) {
            return Optional.empty();
        }
    }

    // Los valores viajan como variables de la plantilla para que se codifiquen por completo (& + = / espacios).
    private static URI searchUri(UriBuilder uri, ProfessionalSearchCriteria criteria) {
        Map<String, Object> variables = new HashMap<>();
        uri.path("/api/professionals/search");
        if (criteria.categoryId() != null) {
            uri.queryParam("categoryId", "{categoryId}");
            variables.put("categoryId", criteria.categoryId());
        }
        if (criteria.name() != null) {
            uri.queryParam("name", "{name}");
            variables.put("name", criteria.name());
        }
        if (criteria.enabled() != null) {
            uri.queryParam("enabled", "{enabled}");
            variables.put("enabled", criteria.enabled());
        }
        if (criteria.availableOn() != null) {
            uri.queryParam("availableOn", "{availableOn}");
            variables.put("availableOn", criteria.availableOn().toString());
        }
        return uri.build(variables);
    }

    private <T> T fetch(Function<UriBuilder, URI> uri, ParameterizedTypeReference<T> type, boolean notFoundAllowed) {
        String token = tokens.token();
        try {
            return call(uri, type, token, notFoundAllowed);
        } catch (TokenRejected first) {
            log.warn("El catálogo rechazó el token técnico (401): se emite uno nuevo y se reintenta una sola vez");
            tokens.discard(token);
            try {
                return call(uri, type, tokens.token(), notFoundAllowed);
            } catch (TokenRejected second) {
                throw new CatalogIntegrationException(
                        "El catálogo rechazó el token técnico incluso después de emitir uno nuevo (HTTP 401)");
            }
        }
    }

    private <T> T call(Function<UriBuilder, URI> uri, ParameterizedTypeReference<T> type, String token,
                       boolean notFoundAllowed) {
        try {
            T body = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(type);
            if (body == null) {
                throw new CatalogIntegrationException("El catálogo respondió con un cuerpo vacío");
            }
            return body;
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new TokenRejected();
        } catch (HttpClientErrorException.Forbidden e) {
            throw new CatalogIntegrationException(
                    "El catálogo respondió 403: el token técnico no tiene el permiso catalog:read", e);
        } catch (HttpClientErrorException.NotFound e) {
            if (notFoundAllowed && PROFESSIONAL_NOT_FOUND.equals(errorCode(e))) {
                throw new ProfessionalNotFound();
            }
            // 404 sin code: la ruta no existe en el catálogo. Es un error de integración, no un "no existe".
            throw new CatalogIntegrationException("El catálogo respondió 404 a una ruta que debería existir", e);
        } catch (HttpClientErrorException e) {
            throw new CatalogIntegrationException("El catálogo respondió HTTP " + e.getStatusCode().value()
                    + ": el pedido de turnos no cumple su contrato", e);
        } catch (HttpServerErrorException e) {
            throw new CatalogUnavailableException("El catálogo respondió HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            // Sin conexión, conexión rechazada o se pasó el tiempo de espera.
            throw new CatalogUnavailableException("No se pudo hablar con el catálogo: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new CatalogIntegrationException("No se pudo leer la respuesta del catálogo", e);
        }
    }

    private static String errorCode(HttpClientErrorException e) {
        try {
            Map<String, Object> body = e.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() {});
            Object code = body == null ? null : body.get("code");
            return code == null ? null : code.toString();
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    // Señales internas: no salen de esta clase.
    private static final class TokenRejected extends RuntimeException {
        TokenRejected() { super(null, null, false, false); }
    }

    private static final class ProfessionalNotFound extends RuntimeException {
        ProfessionalNotFound() { super(null, null, false, false); }
    }
}
