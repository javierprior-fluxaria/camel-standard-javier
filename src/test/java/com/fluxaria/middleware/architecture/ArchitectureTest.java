package com.fluxaria.middleware.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.apache.camel.builder.RouteBuilder;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Pruebas de Arquitectura automatizadas con ArchUnit.
 * Garantizan que las reglas del estándar hexagonal se cumplan en compilación y CI:
 * 1. domain/ no depende de capas externas (inbound, orchestration, outbound) ni de Apache Camel.
 * 2. domain/model/ es 100% agnóstico a frameworks (cero Camel, Spring, Jackson).
 * 3. inbound y outbound son estrictamente independientes (cero llamadas directas entre adaptadores).
 * 4. Toda clase Mapper reside en un paquete 'mapper'.
 * 5. Toda clase RouteBuilder reside en capas autorizadas.
 */
@AnalyzeClasses(packages = "com.fluxaria.middleware", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    @ArchTest
    public static final ArchRule domain_model_must_be_agnostic_to_frameworks =
            classes().that().resideInAPackage("..domain.model..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("..domain.model..", "java..")
                    .as("Las entidades de domain.model deben ser Java puro, agnósticas a frameworks y capas externas");

    @ArchTest
    public static final ArchRule domain_must_not_depend_on_outer_layers =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..inbound..", "..orchestration..", "..outbound..")
                    .as("El núcleo domain no debe depender de inbound, orchestration ni outbound");

    @ArchTest
    public static final ArchRule domain_must_not_depend_on_camel =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.apache.camel..")
                    .as("El núcleo domain no debe importar ninguna clase ni anotación de Apache Camel");

    @ArchTest
    public static final ArchRule inbound_must_not_depend_on_outbound =
            noClasses().that().resideInAPackage("..inbound..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..outbound..")
                    .as("Los adaptadores Inbound no deben tener dependencias directas con adaptadores Outbound");

    @ArchTest
    public static final ArchRule outbound_must_not_depend_on_inbound =
            noClasses().that().resideInAPackage("..outbound..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..inbound..")
                    .as("Los adaptadores Outbound no deben tener dependencias directas con adaptadores Inbound");

    @ArchTest
    public static final ArchRule mappers_must_reside_in_mapper_packages =
            classes().that().haveSimpleNameEndingWith("Mapper")
                    .should().resideInAPackage("..mapper..")
                    .as("Todas las clases que terminan en 'Mapper' deben residir en un paquete ..mapper..");

    @ArchTest
    public static final ArchRule routes_must_reside_in_designated_layers =
            classes().that().areAssignableTo(RouteBuilder.class)
                    .should().resideInAnyPackage("..inbound..", "..orchestration..", "..outbound..", "..backend..", "..shared..")
                    .as("Todas las clases RouteBuilder deben residir en paquetes autorizados de integración");
}
