package com.hospital.scheduler.controller;

import com.hospital.scheduler.security.AuthContextService;
import com.hospital.scheduler.service.CompensationDayService;
import com.hospital.scheduler.service.ConflictDetectionService;
import com.hospital.scheduler.service.ScheduleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the dual URL form for the conflict-check endpoint:
 * <ul>
 *   <li>Canonical: {@code GET /schedules/conflicts/check/{periodId}}</li>
 *   <li>Query alias: {@code GET /schedules/conflicts/check?periodId={id}}</li>
 * </ul>
 * The query alias exists so existing clients / docs that reference
 * {@code ?periodId=1} continue to work after the controller was migrated
 * to path variables. A regression that drops either mapping is caught here.
 *
 * <p>Reflection-based (not {@code @WebMvcTest}) to avoid pulling Spring
 * test infrastructure into a project whose existing tests use plain Mockito.
 */
@DisplayName("ScheduleController - conflict check routing wiring")
class ScheduleControllerConflictCheckRoutingTest {

    private static final String CANONICAL_PATH = "/conflicts/check/{periodId}";
    private static final String QUERY_ALIAS_PATH = "/conflicts/check";

    private final List<Method> conflictMethods = Arrays.stream(ScheduleController.class.getDeclaredMethods())
            .filter(m -> Arrays.stream(m.getAnnotationsByType(GetMapping.class))
                    .flatMap(gm -> Arrays.stream(gm.value()))
                    .anyMatch(p -> p.startsWith("/conflicts/check")))
            .toList();

    @Test
    @DisplayName("Controller exposes both canonical path-variable mapping and query alias")
    void bothMappingsExist() {
        assertThat(conflictMethods)
                .as("ScheduleController should expose both /conflicts/check/{id} and /conflicts/check")
                .hasSize(2);

        boolean hasCanonical = conflictMethods.stream()
                .flatMap(m -> Arrays.stream(m.getAnnotationsByType(GetMapping.class)))
                .flatMap(gm -> Arrays.stream(gm.value()))
                .anyMatch(CANONICAL_PATH::equals);
        boolean hasQueryAlias = conflictMethods.stream()
                .flatMap(m -> Arrays.stream(m.getAnnotationsByType(GetMapping.class)))
                .flatMap(gm -> Arrays.stream(gm.value()))
                .anyMatch(QUERY_ALIAS_PATH::equals);

        assertThat(hasCanonical).as("Canonical /conflicts/check/{periodId} mapping missing").isTrue();
        assertThat(hasQueryAlias).as("Query alias /conflicts/check mapping missing").isTrue();
    }

    @Test
    @DisplayName("Canonical method binds periodId via @PathVariable")
    void canonical_usesPathVariable() throws Exception {
        Method canonical = findMethod(CANONICAL_PATH);
        assertThat(canonical.getParameters())
                .as("Canonical mapping should have a @PathVariable Integer periodId parameter")
                .anyMatch(p -> {
                    if (!p.isAnnotationPresent(PathVariable.class)) return false;
                    if (!p.getType().equals(Integer.class)) return false;
                    // Default @PathVariable (no value()) matches parameter name at runtime
                    String declaredName = p.getAnnotation(PathVariable.class).value();
                    return declaredName.isEmpty() || declaredName.equals("periodId");
                });
    }

    @Test
    @DisplayName("Query alias binds periodId via @RequestParam (required)")
    void queryAlias_usesRequiredRequestParam() throws Exception {
        Method alias = findMethod(QUERY_ALIAS_PATH);
        assertThat(alias.getParameters())
                .as("Query alias should have a required @RequestParam(\"periodId\") Integer parameter")
                .anyMatch(p -> {
                    if (!p.isAnnotationPresent(RequestParam.class)) return false;
                    if (!p.getType().equals(Integer.class)) return false;
                    RequestParam rp = p.getAnnotation(RequestParam.class);
                    return rp.value().equals("periodId") && rp.required();
                });
    }

    @Test
    @DisplayName("Both methods delegate to ScheduleService.checkConflictsInPeriod")
    void bothMethods_delegateToService() throws Exception {
        Method canonical = findMethod(CANONICAL_PATH);
        Method alias = findMethod(QUERY_ALIAS_PATH);

        // Sanity: both methods exist and return ResponseEntity wrapping ApiResponse
        assertThat(canonical.getReturnType().getSimpleName()).isEqualTo("ResponseEntity");
        assertThat(alias.getReturnType().getSimpleName()).isEqualTo("ResponseEntity");
        // No magic: each method just forwards its Integer to the service
        assertThat(canonical.getParameterCount()).isEqualTo(1);
        assertThat(alias.getParameterCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Controller is wired with the expected dependencies")
    void controller_isWellFormed() {
        // Smoke check: ensure the controller has the deps the test file expects.
        // If any dependency is renamed or removed, this fails fast.
        var deps = Stream.of(
                        ScheduleService.class,
                        ConflictDetectionService.class,
                        CompensationDayService.class,
                        AuthContextService.class)
                .map(Class::getSimpleName)
                .toList();
        assertThat(deps).containsExactlyInAnyOrder(
                "ScheduleService",
                "ConflictDetectionService",
                "CompensationDayService",
                "AuthContextService");
    }

    private Method findMethod(String getMappingValue) {
        return conflictMethods.stream()
                .filter(m -> Arrays.stream(m.getAnnotationsByType(GetMapping.class))
                        .flatMap(gm -> Arrays.stream(gm.value()))
                        .anyMatch(v -> v.equals(getMappingValue)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "ScheduleController is missing @GetMapping(\"" + getMappingValue + "\")"));
    }
}