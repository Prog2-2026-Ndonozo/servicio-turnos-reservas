package com.ndonozo.servicioturnosreservas;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.ndonozo.servicioturnosreservas.ArchitectureRules.BASE;

@AnalyzeClasses(packages = BASE + "..", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule MODULO_SOLO_TIENE_TRES_CAPAS = ArchitectureRules.moduloSoloTieneTresCapas(BASE);

    @ArchTest
    static final ArchRule DOMINIO_ES_PURO = ArchitectureRules.DOMINIO_ES_PURO;

    @ArchTest
    static final ArchRule APLICACION_NO_CONOCE_INFRAESTRUCTURA = ArchitectureRules.APLICACION_NO_CONOCE_INFRAESTRUCTURA;

    @ArchTest
    static final ArchRule WEB_NO_VE_PERSISTENCIA_NI_PUERTOS_DE_SALIDA = ArchitectureRules.WEB_NO_VE_PERSISTENCIA_NI_PUERTOS_DE_SALIDA;

    @ArchTest
    static final ArchRule PUERTOS_DE_ENTRADA_SOLO_SE_IMPLEMENTAN_EN_APLICACION = ArchitectureRules.PUERTOS_DE_ENTRADA_SOLO_SE_IMPLEMENTAN_EN_APLICACION;

    @ArchTest
    static final ArchRule SERVICE_SOLO_EN_LAS_FACHADAS = ArchitectureRules.SERVICE_SOLO_EN_LAS_FACHADAS;

    @ArchTest
    static final ArchRule SIN_INYECCION_POR_CAMPO = ArchitectureRules.SIN_INYECCION_POR_CAMPO;

    @ArchTest
    static final ArchRule UN_MODULO_NO_DEPENDE_DE_LA_INFRAESTRUCTURA_DE_OTRO = ArchitectureRules.unModuloNoDependeDeLaInfraestructuraDeOtro(BASE);

    @ArchTest
    static final ArchRule SOLO_LOS_PARES_DE_MODULOS_PERMITIDOS = ArchitectureRules.soloLosParesDeModulosPermitidos(BASE);

    @ArchTest
    static final ArchRule LOS_MODULOS_NO_FORMAN_CICLOS = ArchitectureRules.losModulosNoFormanCiclos(BASE);

    @ArchTest
    static final ArchRule PROFESSIONAL_NO_USA_PERSISTENCIA = ArchitectureRules.professionalNoUsaPersistencia(BASE);
}
