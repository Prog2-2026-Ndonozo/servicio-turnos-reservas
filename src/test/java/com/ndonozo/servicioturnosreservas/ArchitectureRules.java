package com.ndonozo.servicioturnosreservas;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Reglas de arquitectura de este servicio (ADR-0002). Las que dependen de los módulos reciben el paquete base para poder
 * probarse con fixtures; el módulo es el primer paquete debajo de la base, salvo {@code config}.
 */
final class ArchitectureRules {

    static final String BASE = "com.ndonozo.servicioturnosreservas";

    static final String DOMINIO = "..domain..";
    static final String APLICACION = "..application..";
    static final String INFRAESTRUCTURA = "..infrastructure..";
    static final String CONFIG = "..config..";
    static final String PUERTOS_DE_ENTRADA = "..domain.ports.in..";
    static final String PUERTOS_DE_SALIDA = "..domain.ports.out..";
    static final String WEB = "..infrastructure.web..";
    static final String FACHADAS = "..application.service..";
    static final String PERSISTENCIA = "..infrastructure.persistence..";

    /** Grafo de módulos de ADR-0002: origen -> destinos permitidos. Lo que no figura acá está prohibido. */
    static final Map<String, Set<String>> PARES_PERMITIDOS = Map.of(
            "availability", Set.of("professional"),
            "reservation", Set.of("availability"));

    // --- Heredadas del catálogo: sus patrones son por capa y valen para cada módulo ---

    static final ArchRule DOMINIO_ES_PURO = noClasses()
            .that().resideInAPackage(DOMINIO)
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    APLICACION,
                    INFRAESTRUCTURA,
                    CONFIG,
                    "org.springframework..",
                    "jakarta..",
                    "com.fasterxml..")
            .allowEmptyShould(true);

    static final ArchRule APLICACION_NO_CONOCE_INFRAESTRUCTURA = noClasses()
            .that().resideInAPackage(APLICACION)
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    INFRAESTRUCTURA,
                    CONFIG,
                    "org.springframework.web..",
                    "org.springframework.data..",
                    "org.springframework.kafka..",
                    "jakarta.persistence..",
                    "com.fasterxml..")
            .allowEmptyShould(true);

    static final ArchRule WEB_NO_VE_PERSISTENCIA_NI_PUERTOS_DE_SALIDA = noClasses()
            .that().resideInAPackage(WEB)
            .should().dependOnClassesThat()
            .resideInAnyPackage(PUERTOS_DE_SALIDA, PERSISTENCIA)
            .allowEmptyShould(true);

    static final ArchRule PUERTOS_DE_ENTRADA_SOLO_SE_IMPLEMENTAN_EN_APLICACION = classes()
            .that().implement(JavaClass.Predicates.resideInAPackage(PUERTOS_DE_ENTRADA))
            .should().resideInAPackage(APLICACION)
            .allowEmptyShould(true);

    static final ArchRule SERVICE_SOLO_EN_LAS_FACHADAS = classes()
            .that().areAnnotatedWith(Service.class)
            .should().resideInAPackage(FACHADAS)
            .allowEmptyShould(true);

    static final ArchRule SIN_INYECCION_POR_CAMPO = noFields()
            .should().beAnnotatedWith(Autowired.class)
            .allowEmptyShould(true);

    // --- Propias de este servicio: reciben el paquete base ---

    /** Adaptada: todo lo que cuelga de la base, salvo {@code config} y la clase raíz, está en {@code <módulo>.<capa>}. */
    static ArchRule moduloSoloTieneTresCapas(String base) {
        return noClasses()
                .that().resideInAPackage(base + "..")
                .and().resideOutsideOfPackages(base, base + ".config..")
                .should().resideOutsideOfPackages(
                        base + ".*.domain..",
                        base + ".*.application..",
                        base + ".*.infrastructure..")
                .allowEmptyShould(true);
    }

    /** De otro módulo solo se puede usar su {@code domain.model} y sus {@code domain.ports.in}. */
    static ArchRule unModuloNoDependeDeLaInfraestructuraDeOtro(String base) {
        return classes()
                .that().resideInAPackage(base + "..")
                .should(dependenciasEntreModulos(base,
                        "depender de otro módulo solo por su modelo o sus puertos de entrada",
                        (origen, destinoModulo, destino) ->
                                enPaquete(destino, base + "." + destinoModulo + ".domain.model")
                                        || enPaquete(destino, base + "." + destinoModulo + ".domain.ports.in")))
                .allowEmptyShould(true);
    }

    /** Solo existen las dependencias entre módulos declaradas en {@link #PARES_PERMITIDOS}. */
    static ArchRule soloLosParesDeModulosPermitidos(String base) {
        return classes()
                .that().resideInAPackage(base + "..")
                .should(dependenciasEntreModulos(base,
                        "depender solo de los módulos que el grafo permite",
                        (origen, destinoModulo, destino) ->
                                PARES_PERMITIDOS.getOrDefault(origen, Set.of()).contains(destinoModulo)))
                .allowEmptyShould(true);
    }

    /** Los módulos no forman ciclos; {@code config} y la clase raíz quedan fuera porque dependen de todos. */
    static ArchRule losModulosNoFormanCiclos(String base) {
        return slices()
                .assignedFrom(porModulo(base))
                .should().beFreeOfCycles()
                .allowEmptyShould(true);
    }

    /** E §3 y E §4.2: turnos no replica el catálogo; el módulo que lo consulta no tiene acceso a base. */
    static ArchRule professionalNoUsaPersistencia(String base) {
        return noClasses()
                .that().resideInAPackage(base + ".professional..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "jakarta.persistence..",
                        "org.springframework.data..",
                        "org.hibernate..",
                        "org.springframework.jdbc..",
                        "org.springframework.orm..",
                        "java.sql..",
                        "javax.sql..")
                .allowEmptyShould(true);
    }

    // --- Detección de módulos ---

    @FunctionalInterface
    private interface DependenciaPermitida {
        boolean test(String moduloOrigen, String moduloDestino, JavaClass destino);
    }

    private static ArchCondition<JavaClass> dependenciasEntreModulos(
            String base, String descripcion, DependenciaPermitida permitida) {
        return new ArchCondition<>(descripcion) {
            @Override
            public void check(JavaClass origen, ConditionEvents events) {
                Optional<String> moduloOrigen = moduloDe(origen, base);
                if (moduloOrigen.isEmpty()) {
                    return;
                }
                for (Dependency dependencia : origen.getDirectDependenciesFromSelf()) {
                    JavaClass destino = dependencia.getTargetClass().getBaseComponentType();
                    Optional<String> moduloDestino = moduloDe(destino, base);
                    if (moduloDestino.isEmpty() || moduloDestino.equals(moduloOrigen)) {
                        continue;
                    }
                    if (!permitida.test(moduloOrigen.get(), moduloDestino.get(), destino)) {
                        events.add(SimpleConditionEvent.violated(dependencia, dependencia.getDescription()));
                    }
                }
            }
        };
    }

    private static SliceAssignment porModulo(String base) {
        return new SliceAssignment() {
            @Override
            public SliceIdentifier getIdentifierOf(JavaClass clase) {
                return moduloDe(clase, base).map(SliceIdentifier::of).orElseGet(SliceIdentifier::ignore);
            }

            @Override
            public String getDescription() {
                return "módulo de " + base;
            }
        };
    }

    private static Optional<String> moduloDe(JavaClass clase, String base) {
        String paquete = clase.getPackageName();
        if (!paquete.startsWith(base + ".")) {
            return Optional.empty();
        }
        String modulo = paquete.substring(base.length() + 1).split("\\.")[0];
        return modulo.equals("config") ? Optional.empty() : Optional.of(modulo);
    }

    private static boolean enPaquete(JavaClass clase, String paquete) {
        String actual = clase.getPackageName();
        return actual.equals(paquete) || actual.startsWith(paquete + ".");
    }

    private ArchitectureRules() {
    }
}
