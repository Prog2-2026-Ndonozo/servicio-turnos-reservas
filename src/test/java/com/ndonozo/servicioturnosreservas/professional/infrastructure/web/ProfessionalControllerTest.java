package com.ndonozo.servicioturnosreservas.professional.infrastructure.web;

import com.ndonozo.servicioturnosreservas.config.GlobalExceptionHandler;
import com.ndonozo.servicioturnosreservas.config.ProblemJsonSecurityHandlers;
import com.ndonozo.servicioturnosreservas.config.SecurityConfig;
import com.ndonozo.servicioturnosreservas.professional.application.exception.CatalogIntegrationException;
import com.ndonozo.servicioturnosreservas.professional.application.exception.CatalogUnavailableException;
import com.ndonozo.servicioturnosreservas.professional.application.service.ProfessionalService;
import com.ndonozo.servicioturnosreservas.professional.domain.model.Professional;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalCategory;
import com.ndonozo.servicioturnosreservas.professional.domain.model.ProfessionalSearchCriteria;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.web.controller.ProfessionalController;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.web.exception.ProfessionalExceptionHandler;
import com.ndonozo.servicioturnosreservas.professional.infrastructure.web.mapper.ProfessionalDtoMapper;
import com.ndonozo.servicioturnosreservas.user.domain.model.User;
import com.ndonozo.servicioturnosreservas.user.infrastructure.security.JwtTokenIssuer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato REST hacia KMP: filtros, validación, errores del catálogo y protección con el JWT del usuario real. */
@WebMvcTest(ProfessionalController.class)
@Import({SecurityConfig.class, ProblemJsonSecurityHandlers.class, ProfessionalDtoMapper.class,
        ProfessionalExceptionHandler.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.security.jwt.secret=" + ProfessionalControllerTest.SECRETO)
class ProfessionalControllerTest {

    // 64 bytes en Base64, solo para pruebas
    static final String SECRETO =
            "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBw==";
    private static final String BUSQUEDA = "/api/professionals/search";
    private static final String CATEGORIAS = "/api/professional-categories";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfessionalService service;

    // ---------- búsqueda ----------

    @Test
    void la_busqueda_responde_200_con_los_campos_del_catalogo_en_su_mismo_orden() throws Exception {
        when(service.search(any())).thenReturn(List.of(
                Professional.builder().id(15L).categoryId(101L).firstName("Ana").lastName("Gomez").enabled(true).build(),
                Professional.builder().id(9L).categoryId(102L).firstName("Beto").lastName("Paz").enabled(false).build()));

        autenticado(BUSQUEDA)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(15))
                .andExpect(jsonPath("$[0].categoryId").value(101))
                .andExpect(jsonPath("$[0].firstName").value("Ana"))
                .andExpect(jsonPath("$[0].lastName").value("Gomez"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[1].id").value(9))
                .andExpect(jsonPath("$[1].enabled").value(false));
    }

    @Test
    void sin_filtros_no_se_agrega_ningun_valor_por_defecto() throws Exception {
        when(service.search(any())).thenReturn(List.of());

        autenticado(BUSQUEDA).andExpect(status().isOk()).andExpect(content().json("[]"));

        verify(service).search(ProfessionalSearchCriteria.withoutFilters());
    }

    @Test
    void cada_filtro_llega_al_servicio_con_su_tipo() throws Exception {
        when(service.search(any())).thenReturn(List.of());

        autenticado(BUSQUEDA + "?categoryId=101&name=ana&enabled=true&availableOn=2026-11-23").andExpect(status().isOk());

        ArgumentCaptor<ProfessionalSearchCriteria> captor = ArgumentCaptor.forClass(ProfessionalSearchCriteria.class);
        verify(service).search(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new ProfessionalSearchCriteria(101L, "ana", true, LocalDate.of(2026, 11, 23)));
    }

    @Test
    void enabled_false_es_un_filtro_y_no_lo_mismo_que_no_mandarlo() throws Exception {
        when(service.search(any())).thenReturn(List.of());

        autenticado(BUSQUEDA + "?enabled=false").andExpect(status().isOk());

        verify(service).search(new ProfessionalSearchCriteria(null, null, false, null));
    }

    @Test
    void un_nombre_en_blanco_equivale_a_no_filtrar() throws Exception {
        when(service.search(any())).thenReturn(List.of());

        autenticadoConNombre("  ").andExpect(status().isOk());

        verify(service).search(ProfessionalSearchCriteria.withoutFilters());
    }

    @Test
    void los_caracteres_especiales_del_nombre_llegan_intactos() throws Exception {
        when(service.search(any())).thenReturn(List.of());

        autenticadoConNombre("ana & torres+x=1/ñ").andExpect(status().isOk());

        verify(service).search(new ProfessionalSearchCriteria(null, "ana & torres+x=1/ñ", null, null));
    }

    @Test
    void un_parametro_desconocido_se_ignora() throws Exception {
        when(service.search(any())).thenReturn(List.of());

        autenticado(BUSQUEDA + "?otro=1").andExpect(status().isOk());
    }

    @Test
    void el_nombre_acepta_exactamente_100_caracteres() throws Exception {
        when(service.search(any())).thenReturn(List.of());

        autenticado(BUSQUEDA + "?name=" + "a".repeat(100)).andExpect(status().isOk());
    }

    // ---------- validación: se rechaza antes de llamar al catálogo ----------

    @Test
    void los_filtros_invalidos_responden_400_VALIDATION_ERROR_y_nunca_llegan_al_servicio() throws Exception {
        String[][] casos = {
                {"categoryId=0", "categoryId"},
                {"categoryId=-5", "categoryId"},
                {"name=" + "a".repeat(101), "name"},
        };
        for (String[] caso : casos) {
            autenticado(BUSQUEDA + "?" + caso[0])
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[*].field").value(hasItem(caso[1])));
        }
        verifyNoInteractions(service);
    }

    @Test
    void los_valores_mal_formados_responden_400_con_el_campo_y_un_mensaje_generico() throws Exception {
        String[][] casos = {
                {"categoryId=abc", "categoryId"},
                {"enabled=quizas", "enabled"},
                {"availableOn=23/11/2026", "availableOn"},
                {"availableOn=2026-13-45", "availableOn"},
        };
        for (String[] caso : casos) {
            autenticado(BUSQUEDA + "?" + caso[0])
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[?(@.field=='" + caso[1] + "')].message").value("tiene un formato inválido"))
                    // el mensaje no repite el valor enviado ni expone nombres de clases de Spring
                    .andExpect(content().string(not(containsString("java.lang"))))
                    .andExpect(content().string(not(containsString("quizas"))));
        }
        verifyNoInteractions(service);
    }

    // ---------- categorías ----------

    @Test
    void las_categorias_responden_200_con_sus_campos_incluidas_las_deshabilitadas() throws Exception {
        when(service.categories()).thenReturn(List.of(
                ProfessionalCategory.builder().id(101L).name("Cardiologia").description("Cardio").enabled(true).build(),
                ProfessionalCategory.builder().id(109L).name("Vieja").description("").enabled(false).build()));

        autenticado(CATEGORIAS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].name").value("Cardiologia"))
                .andExpect(jsonPath("$[0].description").value("Cardio"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[1].enabled").value(false));
    }

    // ---------- errores del catálogo ----------

    @Test
    void catalogo_no_disponible_responde_503_CATALOG_UNAVAILABLE_sin_filtrar_detalles_internos() throws Exception {
        when(service.categories()).thenThrow(new CatalogUnavailableException(
                "No se pudo hablar con el catálogo: connect timed out http://catalogo-interno:8080"));

        autenticado(CATEGORIAS)
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("catalogo-interno"))))
                .andExpect(content().string(not(containsString("timed out"))));
    }

    @Test
    void error_de_integracion_responde_502_CATALOG_ERROR_sin_filtrar_detalles_internos() throws Exception {
        when(service.search(any())).thenThrow(new CatalogIntegrationException(
                "El catálogo rechazó el token técnico incluso después de emitir uno nuevo (HTTP 401)"));

        autenticado(BUSQUEDA)
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CATALOG_ERROR"))
                .andExpect(content().string(not(containsString("token"))))
                .andExpect(content().string(not(containsString("401"))));
    }

    // ---------- protección con el JWT del usuario (decodificador real) ----------

    @Test
    void sin_token_ninguna_de_las_dos_rutas_responde_y_el_servicio_no_se_toca() throws Exception {
        for (String ruta : new String[]{BUSQUEDA, CATEGORIAS}) {
            mockMvc.perform(get(ruta))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void un_token_alterado_o_de_texto_cualquiera_responde_401_en_las_dos_rutas() throws Exception {
        String valido = tokenDeUsuario();
        for (String ruta : new String[]{BUSQUEDA, CATEGORIAS}) {
            for (String token : new String[]{valido.substring(0, valido.length() - 3) + "abc", "esto-no-es-un-jwt"}) {
                mockMvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            }
        }
        verifyNoInteractions(service);
    }

    @Test
    void un_token_valido_pasa_la_seguridad_en_las_dos_rutas() throws Exception {
        when(service.search(any())).thenReturn(List.of());
        when(service.categories()).thenReturn(List.of());

        autenticado(BUSQUEDA).andExpect(status().isOk());
        autenticado(CATEGORIAS).andExpect(status().isOk());
    }

    // ---------- soporte ----------

    private ResultActions autenticado(String urlConQuery) throws Exception {
        return mockMvc.perform(get(urlConQuery).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario()));
    }

    // queryParam codifica el valor una sola vez; get("...%20...") lo volvería a codificar.
    private ResultActions autenticadoConNombre(String nombre) throws Exception {
        return mockMvc.perform(get(BUSQUEDA).queryParam("name", nombre)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUsuario()));
    }

    private static String tokenDeUsuario() {
        User ana = User.builder().id(42L).login("ana").authorities(Set.of(User.ROLE_USER)).build();
        return new JwtTokenIssuer(SECRETO, Duration.ofHours(1)).issue(ana).value();
    }
}
