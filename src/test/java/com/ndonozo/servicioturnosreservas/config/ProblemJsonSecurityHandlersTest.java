package com.ndonozo.servicioturnosreservas.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemJsonSecurityHandlersTest {

    private final ProblemJsonSecurityHandlers handlers = new ProblemJsonSecurityHandlers(JsonMapper.builder().build());

    @Test
    void el_403_sale_en_problem_json_con_el_code_FORBIDDEN() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.handle(new MockHttpServletRequest(), response, new AccessDeniedException("no"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"").contains("\"status\":403");
    }

    @Test
    void el_401_sale_en_problem_json_con_el_code_UNAUTHENTICATED_y_la_cabecera_Bearer() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.commence(new MockHttpServletRequest(), response, new BadCredentialsException("no"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHENTICATED\"");
    }
}
