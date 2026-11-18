package com.ndonozo.servicioturnosreservas;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cada regla se prueba contra un fixture que la viola (debe detectarlo) y, las propias de este servicio, contra otro que
 * la respeta (no debe bloquear de más). Los fixtures viven en {@code com.ndonozo.arquitecturafixtures}.
 */
class ArchitectureRulesFixtureTest {

    private static final String FIXTURES = "com.ndonozo.arquitecturafixtures.";

    // --- Heredadas ---

    @Test
    void DominioEsPuro_detecta_un_import_de_framework() {
        assertViolacion(ArchitectureRules.DOMINIO_ES_PURO, "violarDominioEsPuro");
    }

    @Test
    void AplicacionNoConoceInfraestructura_detecta_un_import_de_infrastructure() {
        assertViolacion(ArchitectureRules.APLICACION_NO_CONOCE_INFRAESTRUCTURA, "violarAplicacionNoConoceInfraestructura");
    }

    @Test
    void WebNoVePersistenciaNiPuertosDeSalida_detecta_un_import_de_ports_out() {
        assertViolacion(ArchitectureRules.WEB_NO_VE_PERSISTENCIA_NI_PUERTOS_DE_SALIDA, "violarWebNoVePersistenciaNiPuertosDeSalida");
    }

    @Test
    void PuertosDeEntradaSoloSeImplementanEnAplicacion_detecta_un_use_case_en_el_dominio() {
        assertViolacion(ArchitectureRules.PUERTOS_DE_ENTRADA_SOLO_SE_IMPLEMENTAN_EN_APLICACION, "violarPuertosDeEntradaSoloSeImplementanEnAplicacion");
    }

    @Test
    void ServiceSoloEnLasFachadas_detecta_un_service_en_usecases() {
        assertViolacion(ArchitectureRules.SERVICE_SOLO_EN_LAS_FACHADAS, "violarServiceSoloEnLasFachadas");
    }

    @Test
    void SinInyeccionPorCampo_detecta_un_autowired_en_un_campo() {
        assertViolacion(ArchitectureRules.SIN_INYECCION_POR_CAMPO, "violarSinInyeccionPorCampo");
    }

    // --- Propias: cada una con un fixture que viola y otro que respeta ---

    @Test
    void ModuloSoloTieneTresCapas_detecta_una_clase_de_un_modulo_fuera_de_las_tres_capas() {
        assertViolacion(ArchitectureRules.moduloSoloTieneTresCapas(base("violarModuloSoloTieneTresCapas")), "violarModuloSoloTieneTresCapas");
    }

    @Test
    void ModuloSoloTieneTresCapas_acepta_las_tres_capas_config_y_la_clase_raiz() {
        assertSinViolacion(ArchitectureRules.moduloSoloTieneTresCapas(base("respetarModuloSoloTieneTresCapas")), "respetarModuloSoloTieneTresCapas");
    }

    @Test
    void UnModuloNoDependeDeLaInfraestructuraDeOtro_detecta_el_uso_de_una_fachada_ajena() {
        assertViolacion(ArchitectureRules.unModuloNoDependeDeLaInfraestructuraDeOtro(base("violarEntreModulos")), "violarEntreModulos");
    }

    @Test
    void UnModuloNoDependeDeLaInfraestructuraDeOtro_acepta_el_modelo_y_los_puertos_de_entrada() {
        assertSinViolacion(ArchitectureRules.unModuloNoDependeDeLaInfraestructuraDeOtro(base("respetarEntreModulos")), "respetarEntreModulos");
    }

    @Test
    void SoloLosParesDeModulosPermitidos_detecta_una_dependencia_que_el_grafo_no_declara() {
        assertViolacion(ArchitectureRules.soloLosParesDeModulosPermitidos(base("violarParesDeModulos")), "violarParesDeModulos");
    }

    @Test
    void SoloLosParesDeModulosPermitidos_acepta_availability_a_professional_y_reservation_a_availability() {
        assertSinViolacion(ArchitectureRules.soloLosParesDeModulosPermitidos(base("respetarParesDeModulos")), "respetarParesDeModulos");
    }

    @Test
    void LosModulosNoFormanCiclos_detecta_un_ciclo_entre_dos_modulos() {
        assertViolacion(ArchitectureRules.losModulosNoFormanCiclos(base("violarCiclosEntreModulos")), "violarCiclosEntreModulos");
    }

    @Test
    void LosModulosNoFormanCiclos_ignora_a_config_que_depende_de_todos() {
        assertSinViolacion(ArchitectureRules.losModulosNoFormanCiclos(base("respetarCiclosEntreModulos")), "respetarCiclosEntreModulos");
    }

    @Test
    void ProfessionalNoUsaPersistencia_detecta_una_entidad_jpa_en_professional() {
        assertViolacion(ArchitectureRules.professionalNoUsaPersistencia(base("violarProfessionalConPersistencia")), "violarProfessionalConPersistencia");
    }

    @Test
    void ProfessionalNoUsaPersistencia_no_bloquea_a_otro_modulo_con_jpa() {
        assertSinViolacion(ArchitectureRules.professionalNoUsaPersistencia(base("respetarProfessionalConPersistencia")), "respetarProfessionalConPersistencia");
    }

    private static String base(String paquete) {
        return FIXTURES + paquete;
    }

    private static void assertViolacion(ArchRule regla, String paquete) {
        assertThat(regla.evaluate(importar(paquete)).hasViolation())
                .as("la regla debia reportar una violacion sobre el fixture %s", paquete)
                .isTrue();
    }

    private static void assertSinViolacion(ArchRule regla, String paquete) {
        assertThat(regla.evaluate(importar(paquete)).hasViolation())
                .as("la regla no debia reportar violaciones sobre el fixture %s", paquete)
                .isFalse();
    }

    private static JavaClasses importar(String paquete) {
        return new ClassFileImporter().importPackages(FIXTURES + paquete);
    }
}
