package com.hospital.scheduler.whatif.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.whatif.dto.*;
import com.hospital.scheduler.whatif.entity.WhatifScenario;
import com.hospital.scheduler.whatif.repository.WhatifScenarioRepository;
import com.hospital.scheduler.digital.sandbox.entity.SandboxSession;
import com.hospital.scheduler.digital.sandbox.service.SandboxService;
import com.hospital.scheduler.security.AuthContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for what-if scenario management.
 *
 * <p>Handles:
 * <ul>
 *   <li>Scenario CRUD</li>
 *   <li>Simulation running</li>
 *   <li>Comparison</li>
 *   <li>Impact analysis</li>
 *   <li>Recommendations</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatifScenarioService {

    private final WhatifScenarioRepository scenarioRepository;
    private final SandboxService sandboxService;
    private final ObjectMapper objectMapper;
    private final AuthContextService authContextService;

    // ─── CRUD ───────────────────────────────────────────────────────────────

    /**
     * Create a new scenario.
     */
    @Transactional
    public ScenarioResponse createScenario(ScenarioRequest request) {
        WhatifScenario scenario = WhatifScenario.builder()
                .name(request.getName())
                .description(request.getDescription())
                .sourcePeriodId(request.getSourcePeriodId())
                .parentScenarioId(request.getParentScenarioId())
                .tags(request.getTags())
                .status(WhatifScenario.ScenarioStatus.DRAFT)
                .build();

        if (request.getConfigOverrides() != null) {
            try {
                scenario.setConfigOverrides(objectMapper.writeValueAsString(request.getConfigOverrides()));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize config overrides", e);
            }
        }

        scenario = scenarioRepository.save(scenario);
        return toResponse(scenario);
    }

    /**
     * Get scenario by ID.
     */
    @Transactional(readOnly = true)
    public ScenarioResponse getScenario(Integer id) {
        WhatifScenario scenario = scenarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + id));
        return toResponse(scenario);
    }

    /**
     * Get all scenarios.
     */
    @Transactional(readOnly = true)
    public List<ScenarioResponse> getAllScenarios() {
        return scenarioRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Update scenario.
     */
    @Transactional
    public ScenarioResponse updateScenario(Integer id, ScenarioRequest request) {
        WhatifScenario scenario = scenarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + id));

        if (request.getName() != null) scenario.setName(request.getName());
        if (request.getDescription() != null) scenario.setDescription(request.getDescription());
        if (request.getConfigOverrides() != null) {
            try {
                scenario.setConfigOverrides(objectMapper.writeValueAsString(request.getConfigOverrides()));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize config overrides", e);
            }
        }
        if (request.getTags() != null) scenario.setTags(request.getTags());

        scenario.setUpdatedAt(LocalDateTime.now());
        scenario = scenarioRepository.save(scenario);
        return toResponse(scenario);
    }

    /**
     * Delete scenario.
     */
    @Transactional
    public void deleteScenario(Integer id) {
        if (!scenarioRepository.existsById(id)) {
            throw new IllegalArgumentException("Scenario not found: " + id);
        }
        scenarioRepository.deleteById(id);
    }

    // ─── Simulation ──────────────────────────────────────────────────────────

    /**
     * Run a scenario simulation.
     */
    @Transactional
    public ScenarioResponse runScenario(Integer id) {
        WhatifScenario scenario = scenarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + id));

        // Update status
        scenario.setStatus(WhatifScenario.ScenarioStatus.RUNNING);
        scenarioRepository.save(scenario);

        try {
            // Get config overrides
            Map<String, Object> configOverrides = parseConfigOverrides(scenario.getConfigOverrides());

            // Run sandbox simulation
            SandboxSession session = sandboxService.createSandbox(
                    scenario.getSourcePeriodId(),
                    null, // profileId
                    // BUGFIX: was null — this let Whatif scenarios bypass the per-user
                    // sandbox quota (MAX_SESSIONS_PER_USER) because the user-limit
                    // counter in SandboxCleanupService filters by createdBy. Also,
                    // scenario.getCreatedBy() is Integer (staffId) but createSandbox
                    // expects String username, so we resolve it via AuthContextService.
                    // (The runScenario endpoint requires authentication, so the current
                    // user IS the scenario owner.)
                    authContextService.getCurrentStaff().getUsername(),
                    scenario.getName(),
                    null, // simulationMode
                    null  // ttlHours
            );
            // Start the simulation
            session = sandboxService.start(session.getSessionKey());

            // Update with results
            scenario.setSessionKey(session.getSessionKey());
            scenario.setStatus(WhatifScenario.ScenarioStatus.COMPLETED);
            scenario.setSimulationDurationMs(session.getRuntimeSeconds() != null ? (long) session.getRuntimeSeconds() * 1000 : null);
            scenario.setResults(objectMapper.writeValueAsString(buildResults(session)));

            scenarioRepository.save(scenario);

            return toResponse(scenario);

        } catch (Exception e) {
            log.error("Failed to run scenario", e);
            scenario.setStatus(WhatifScenario.ScenarioStatus.FAILED);
            scenarioRepository.save(scenario);
            throw new RuntimeException("Simulation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Run multiple scenarios in batch.
     */
    @Transactional
    public List<ScenarioResponse> runBatch(List<Integer> scenarioIds) {
        List<ScenarioResponse> results = new ArrayList<>();

        for (Integer id : scenarioIds) {
            try {
                results.add(runScenario(id));
            } catch (Exception e) {
                log.error("Failed to run scenario {}", id, e);
                WhatifScenario scenario = scenarioRepository.findById(id).orElse(null);
                if (scenario != null) {
                    results.add(toResponse(scenario));
                }
            }
        }

        return results;
    }

    // ─── Comparison ──────────────────────────────────────────────────────────

    /**
     * Compare two scenarios.
     */
    @Transactional(readOnly = true)
    public ScenarioComparison compare(Integer baselineId, Integer comparedId) {
        WhatifScenario baseline = scenarioRepository.findById(baselineId)
                .orElseThrow(() -> new IllegalArgumentException("Baseline scenario not found"));
        WhatifScenario compared = scenarioRepository.findById(comparedId)
                .orElseThrow(() -> new IllegalArgumentException("Compared scenario not found"));

        ScenarioResponse baselineResult = toResponse(baseline);
        ScenarioResponse comparedResult = toResponse(compared);

        ScenarioResponse.ScenarioResult bResult = baselineResult.getResults();
        ScenarioResponse.ScenarioResult cResult = comparedResult.getResults();

        // Build metrics
        ScenarioComparison.ComparisonMetrics metrics = ScenarioComparison.ComparisonMetrics.builder()
                .baselineCoverage(bResult != null ? bResult.getCoverage() : null)
                .comparedCoverage(cResult != null ? cResult.getCoverage() : null)
                .coverageDelta(delta(bResult, cResult, ScenarioResponse.ScenarioResult::getCoverage))

                .baselineFairness(bResult != null ? bResult.getFairness() : null)
                .comparedFairness(cResult != null ? cResult.getFairness() : null)
                .fairnessDelta(delta(bResult, cResult, ScenarioResponse.ScenarioResult::getFairness))

                .baselineViolations(bResult != null ? bResult.getViolations() : null)
                .comparedViolations(cResult != null ? cResult.getViolations() : null)
                .violationsDelta(deltaInt(bResult, cResult, ScenarioResponse.ScenarioResult::getViolations))

                .baselineRuntime(bResult != null ? bResult.getRuntimeMs() : null)
                .comparedRuntime(cResult != null ? cResult.getRuntimeMs() : null)
                .runtimeDelta(deltaRuntime(bResult, cResult))

                .baselineScore(bResult != null ? bResult.getScore() : null)
                .comparedScore(cResult != null ? cResult.getScore() : null)
                .scoreDelta(delta(bResult, cResult, ScenarioResponse.ScenarioResult::getScore))
                .build();

        // Build changes
        Map<String, ScenarioComparison.MetricChange> changes = new LinkedHashMap<>();

        if (metrics.getCoverageDelta() != null) {
            changes.put("coverage", ScenarioComparison.MetricChange.builder()
                    .metricName("Coverage")
                    .changeType(metrics.getCoverageDelta() > 0 ? "IMPROVED" : "DEGRADED")
                    .absoluteChange(metrics.getCoverageDelta())
                    .percentChange(percentChange(metrics.getBaselineCoverage(), metrics.getComparedCoverage()))
                    .impact(metrics.getCoverageDelta() > 1 ? "HIGH" : "LOW")
                    .description(String.format("Coverage changed by %.1f%%", metrics.getCoverageDelta()))
                    .build());
        }

        if (metrics.getFairnessDelta() != null) {
            changes.put("fairness", ScenarioComparison.MetricChange.builder()
                    .metricName("Fairness (CV)")
                    .changeType(metrics.getFairnessDelta() < 0 ? "IMPROVED" : "DEGRADED")
                    .absoluteChange(metrics.getFairnessDelta())
                    .percentChange(percentChange(metrics.getBaselineFairness(), metrics.getComparedFairness()))
                    .impact(Math.abs(metrics.getFairnessDelta()) > 0.02 ? "HIGH" : "LOW")
                    .description(String.format("Fairness CV changed by %.3f", metrics.getFairnessDelta()))
                    .build());
        }

        if (metrics.getViolationsDelta() != null) {
            changes.put("violations", ScenarioComparison.MetricChange.builder()
                    .metricName("Violations")
                    .changeType(metrics.getViolationsDelta() < 0 ? "IMPROVED" : "DEGRADED")
                    .absoluteChange((double) metrics.getViolationsDelta())
                    .percentChange(percentChange((double) metrics.getBaselineViolations(), (double) metrics.getComparedViolations()))
                    .impact(Math.abs(metrics.getViolationsDelta()) > 3 ? "HIGH" : "LOW")
                    .description(String.format("%d violations %s", Math.abs(metrics.getViolationsDelta()),
                            metrics.getViolationsDelta() < 0 ? "reduced" : "added"))
                    .build());
        }

        return ScenarioComparison.builder()
                .baselineId(baselineId)
                .comparedId(comparedId)
                .metrics(metrics)
                .changes(changes)
                .recommendation(buildRecommendation(metrics))
                .build();
    }

    // ─── Impact Analysis ─────────────────────────────────────────────────────

    /**
     * Analyze impact of configuration changes.
     */
    @Transactional(readOnly = true)
    public ImpactAnalysis analyzeImpact(Integer scenarioId) {
        WhatifScenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new IllegalArgumentException("Scenario not found"));

        Map<String, Object> configOverrides = parseConfigOverrides(scenario.getConfigOverrides());
        List<ImpactAnalysis.ConfigImpact> configImpacts = new ArrayList<>();

        for (Map.Entry<String, Object> entry : configOverrides.entrySet()) {
            ImpactAnalysis.ConfigImpact impact = analyzeConfigImpact(entry.getKey(), entry.getValue());
            configImpacts.add(impact);
        }

        // Build summary
        ImpactAnalysis.ImpactSummary summary = ImpactAnalysis.ImpactSummary.builder()
                .overallImpact(calculateOverallImpact(configImpacts))
                .confidenceScore(0.75) // Placeholder
                .summary(buildImpactSummary(configImpacts))
                .keyFindings(extractKeyFindings(configImpacts))
                .build();

        return ImpactAnalysis.builder()
                .scenarioId(scenarioId)
                .configImpacts(configImpacts)
                .summary(summary)
                .predictedMetrics(buildPredictedMetrics(configImpacts))
                .warnings(generateWarnings(configImpacts))
                .build();
    }

    // ─── Recommendations ─────────────────────────────────────────────────────

    /**
     * Get recommendations for a scenario.
     */
    @Transactional(readOnly = true)
    public List<Recommendation> getRecommendations(Integer scenarioId) {
        WhatifScenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new IllegalArgumentException("Scenario not found"));

        List<Recommendation> recommendations = new ArrayList<>();

        // Analyze config and generate recommendations
        Map<String, Object> configOverrides = parseConfigOverrides(scenario.getConfigOverrides());

        int priority = 1;

        for (Map.Entry<String, Object> entry : configOverrides.entrySet()) {
            Recommendation rec = generateRecommendation(priority++, entry.getKey(), entry.getValue());
            if (rec != null) {
                recommendations.add(rec);
            }
        }

        return recommendations;
    }

    // ─── Helper Methods ──────────────────────────────────────────────────────

    private ScenarioResponse toResponse(WhatifScenario scenario) {
        ScenarioResponse.ScenarioResult result = null;

        if (scenario.getResults() != null) {
            try {
                result = objectMapper.readValue(scenario.getResults(), ScenarioResponse.ScenarioResult.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse results", e);
            }
        }

        return ScenarioResponse.builder()
                .id(scenario.getId())
                .name(scenario.getName())
                .description(scenario.getDescription())
                .baseline(scenario.isBaseline())
                .sourcePeriodId(scenario.getSourcePeriodId())
                .configOverrides(parseConfigOverrides(scenario.getConfigOverrides()))
                .status(scenario.getStatus().name())
                .results(result)
                .simulationDurationMs(scenario.getSimulationDurationMs())
                .sessionKey(scenario.getSessionKey())
                .createdAt(scenario.getCreatedAt())
                .updatedAt(scenario.getUpdatedAt())
                .createdBy(scenario.getCreatedBy())
                .parentScenarioId(scenario.getParentScenarioId())
                .tags(scenario.getTags())
                .build();
    }

    private Map<String, Object> parseConfigOverrides(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse config overrides", e);
            return Collections.emptyMap();
        }
    }

    private ScenarioResponse.ScenarioResult buildResults(SandboxSession session) {
        Long runtimeMs = session.getRuntimeSeconds() != null ? (long) session.getRuntimeSeconds() * 1000 : null;
        return ScenarioResponse.ScenarioResult.builder()
                .coverage(session.getCoverageRate())
                .fairness(session.getFairnessCv())
                .violations(session.getViolations())
                .iterations(session.getIterations())
                .runtimeMs(runtimeMs)
                .score(session.getBestScore())
                .build();
    }

    private Double delta(ScenarioResponse.ScenarioResult a, ScenarioResponse.ScenarioResult b,
                        java.util.function.ToDoubleFunction<ScenarioResponse.ScenarioResult> getter) {
        if (a == null || b == null) return null;
        return getter.applyAsDouble(b) - getter.applyAsDouble(a);
    }

    private Integer deltaInt(ScenarioResponse.ScenarioResult a, ScenarioResponse.ScenarioResult b,
                            java.util.function.ToIntFunction<ScenarioResponse.ScenarioResult> getter) {
        if (a == null || b == null) return null;
        return getter.applyAsInt(b) - getter.applyAsInt(a);
    }

    private Double deltaRuntime(ScenarioResponse.ScenarioResult a, ScenarioResponse.ScenarioResult b) {
        if (a == null || b == null || a.getRuntimeMs() == null || b.getRuntimeMs() == null) return null;
        return (b.getRuntimeMs() - a.getRuntimeMs()) / 1000.0;
    }

    private Double percentChange(Double a, Double b) {
        if (a == null || b == null || a == 0) return null;
        return ((b - a) / a) * 100;
    }

    private String buildRecommendation(ScenarioComparison.ComparisonMetrics metrics) {
        if (metrics.getCoverageDelta() != null && metrics.getCoverageDelta() > 1) {
            return "Scenario shows improved coverage. Consider applying.";
        }
        if (metrics.getViolationsDelta() != null && metrics.getViolationsDelta() < -3) {
            return "Scenario significantly reduces violations. Recommend testing.";
        }
        if (metrics.getFairnessDelta() != null && Math.abs(metrics.getFairnessDelta()) > 0.02) {
            return "Fairness changed. Review impact on schedule distribution.";
        }
        return "No significant improvement detected.";
    }

    private ImpactAnalysis.ConfigImpact analyzeConfigImpact(String key, Object value) {
        String category = categorizeConfig(key);
        ImpactAnalysis.ImpactLevel level = estimateImpactLevel(key, value);

        return ImpactAnalysis.ConfigImpact.builder()
                .configKey(key)
                .newValue(value)
                .category(category)
                .impactLevel(level)
                .impactScore(calculateImpactScore(level))
                .affectedMetrics(getAffectedMetrics(key))
                .build();
    }

    private String categorizeConfig(String key) {
        if (key.contains("tabu") || key.contains("Tabu")) return "TABU";
        if (key.contains("coverage") || key.contains("Coverage")) return "COVERAGE";
        if (key.contains("fairness") || key.contains("Fairness")) return "FAIRNESS";
        if (key.contains("max") || key.contains("Max")) return "CONSTRAINT";
        return "OTHER";
    }

    private ImpactAnalysis.ImpactLevel estimateImpactLevel(String key, Object value) {
        if (key.contains("tabu")) return ImpactAnalysis.ImpactLevel.MEDIUM;
        if (key.contains("max") || key.contains("Min")) return ImpactAnalysis.ImpactLevel.HIGH;
        return ImpactAnalysis.ImpactLevel.LOW;
    }

    private double calculateImpactScore(ImpactAnalysis.ImpactLevel level) {
        return switch (level) {
            case HIGH -> 0.8;
            case MEDIUM -> 0.5;
            case LOW -> 0.2;
        };
    }

    private List<String> getAffectedMetrics(String key) {
        if (key.contains("tabu")) return List.of("Runtime", "Score");
        if (key.contains("coverage")) return List.of("Coverage", "Violations");
        if (key.contains("fairness")) return List.of("Fairness", "Distribution");
        return List.of("Score");
    }

    private String calculateOverallImpact(List<ImpactAnalysis.ConfigImpact> impacts) {
        long highCount = impacts.stream().filter(i -> i.getImpactLevel() == ImpactAnalysis.ImpactLevel.HIGH).count();
        if (highCount > 0) return "MIXED";
        return "NEUTRAL";
    }

    private String buildImpactSummary(List<ImpactAnalysis.ConfigImpact> impacts) {
        return "Configuration changes may affect runtime and constraint satisfaction.";
    }

    private List<String> extractKeyFindings(List<ImpactAnalysis.ConfigImpact> impacts) {
        return impacts.stream()
                .filter(i -> i.getImpactLevel() == ImpactAnalysis.ImpactLevel.HIGH)
                .map(i -> "High impact: " + i.getConfigKey())
                .collect(Collectors.toList());
    }

    private ImpactAnalysis.PredictedMetrics buildPredictedMetrics(List<ImpactAnalysis.ConfigImpact> impacts) {
        return ImpactAnalysis.PredictedMetrics.builder()
                .coverage(0.0)
                .coverageDelta(0.0)
                .coverageConfidence(0.7)
                .fairness(0.1)
                .fairnessDelta(0.0)
                .fairnessConfidence(0.7)
                .violations(0)
                .violationsDelta(0)
                .violationsConfidence(0.7)
                .score(0.0)
                .scoreDelta(0.0)
                .scoreConfidence(0.7)
                .estimatedRuntime(3000L)
                .runtimeDelta(0.0)
                .build();
    }

    private List<ImpactAnalysis.ImpactWarning> generateWarnings(List<ImpactAnalysis.ConfigImpact> impacts) {
        return impacts.stream()
                .filter(i -> i.getImpactLevel() == ImpactAnalysis.ImpactLevel.HIGH)
                .map(i -> ImpactAnalysis.ImpactWarning.builder()
                        .warningType("CONSTRAINT")
                        .message("High impact change: " + i.getConfigKey())
                        .severity("MEDIUM")
                        .affectedMetric("Multiple")
                        .build())
                .collect(Collectors.toList());
    }

    private Recommendation generateRecommendation(int priority, String key, Object value) {
        return Recommendation.builder()
                .id(priority)
                .priority(priority)
                .category(categorizeConfig(key))
                .title("Consider adjusting " + key)
                .description("Parameter " + key + " set to " + value)
                .recommendedValue(value)
                .expectedImpact(Recommendation.ExpectedImpact.builder().build())
                .confidence(0.7)
                .reason("Based on sensitivity analysis")
                .riskAssessment(Recommendation.RiskAssessment.builder()
                        .level("LOW")
                        .risks(List.of("May affect other metrics"))
                        .mitigations(List.of("Test thoroughly"))
                        .build())
                .build();
    }
}
