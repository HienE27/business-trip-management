package com.hospital.scheduler.whatif.controller;

import com.hospital.scheduler.whatif.dto.*;
import com.hospital.scheduler.whatif.service.WhatifScenarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for what-if scenario management.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET/POST/PUT/DELETE /what-if/scenarios</li>
 *   <li>POST /what-if/{id}/run</li>
 *   <li>POST /what-if/run-batch</li>
 *   <li>GET /what-if/{id}/compare</li>
 *   <li>GET /what-if/{id}/impact</li>
 *   <li>GET /what-if/{id}/recommendations</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/what-if")
@RequiredArgsConstructor
@Slf4j
public class WhatifScenarioController {

    private final WhatifScenarioService scenarioService;

    // ─── Scenario CRUD ─────────────────────────────────────────────────────

    /**
     * Get all scenarios.
     */
    @GetMapping("/scenarios")
    public ResponseEntity<List<ScenarioResponse>> getAllScenarios() {
        return ResponseEntity.ok(scenarioService.getAllScenarios());
    }

    /**
     * Create a new scenario.
     */
    @PostMapping("/scenarios")
    public ResponseEntity<ScenarioResponse> createScenario(@RequestBody ScenarioRequest request) {
        return ResponseEntity.ok(scenarioService.createScenario(request));
    }

    /**
     * Get scenario by ID.
     */
    @GetMapping("/scenarios/{id}")
    public ResponseEntity<ScenarioResponse> getScenario(@PathVariable Integer id) {
        return ResponseEntity.ok(scenarioService.getScenario(id));
    }

    /**
     * Update scenario.
     */
    @PutMapping("/scenarios/{id}")
    public ResponseEntity<ScenarioResponse> updateScenario(
            @PathVariable Integer id,
            @RequestBody ScenarioRequest request
    ) {
        return ResponseEntity.ok(scenarioService.updateScenario(id, request));
    }

    /**
     * Delete scenario.
     */
    @DeleteMapping("/scenarios/{id}")
    public ResponseEntity<Void> deleteScenario(@PathVariable Integer id) {
        scenarioService.deleteScenario(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Simulation ────────────────────────────────────────────────────────

    /**
     * Run a scenario.
     */
    @PostMapping("/{id}/run")
    public ResponseEntity<ScenarioResponse> runScenario(@PathVariable Integer id) {
        return ResponseEntity.ok(scenarioService.runScenario(id));
    }

    /**
     * Run multiple scenarios in batch.
     */
    @PostMapping("/run-batch")
    public ResponseEntity<List<ScenarioResponse>> runBatch(@RequestBody List<Integer> scenarioIds) {
        return ResponseEntity.ok(scenarioService.runBatch(scenarioIds));
    }

    // ─── Analysis ─────────────────────────────────────────────────────────

    /**
     * Compare two scenarios.
     */
    @GetMapping("/compare")
    public ResponseEntity<ScenarioComparison> compare(
            @RequestParam Integer baselineId,
            @RequestParam Integer comparedId
    ) {
        return ResponseEntity.ok(scenarioService.compare(baselineId, comparedId));
    }

    /**
     * Analyze impact of a scenario.
     */
    @GetMapping("/{id}/impact")
    public ResponseEntity<ImpactAnalysis> analyzeImpact(@PathVariable Integer id) {
        return ResponseEntity.ok(scenarioService.analyzeImpact(id));
    }

    /**
     * Get recommendations for a scenario.
     */
    @GetMapping("/{id}/recommendations")
    public ResponseEntity<List<Recommendation>> getRecommendations(@PathVariable Integer id) {
        return ResponseEntity.ok(scenarioService.getRecommendations(id));
    }
}
