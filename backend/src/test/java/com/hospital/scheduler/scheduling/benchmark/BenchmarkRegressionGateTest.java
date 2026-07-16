package com.hospital.scheduler.scheduling.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 1.5 — CI gate. Compares per-dataset benchmark CSVs against
 * {@code baseline.json}. Any dataset regressing more than
 * {@code regression_threshold_percent} blocks CI.
 *
 * <p>Disabled by default. Run with {@code -Dbenchmark=true} after the
 * {@link BenchmarkSuiteTest} has produced CSVs under {@code target/benchmarks}.
 */
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
class BenchmarkRegressionGateTest {

    private static final double REGRESSION_PCT = 20.0;

    @Test
    void benchmarkWithinBudget() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode baseline = mapper.readTree(new File(
                "src/test/resources/benchmarks/baseline.json"));
        double threshold = baseline.get("regression_threshold_percent").asDouble(REGRESSION_PCT);
        JsonNode datasets = baseline.get("datasets");

        StringBuilder errors = new StringBuilder();
        var fields = datasets.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String name = entry.getKey();
            long baseTime = entry.getValue().get("baseline_time_ms").asLong();
            File csv = new File("target/benchmarks/v10-" + name + ".csv");
            if (!csv.exists()) {
                errors.append("missing CSV for ").append(name).append('\n');
                continue;
            }
            long actual = parseCsvTimeMs(csv);
            double driftPct = ((double) actual - baseTime) / baseTime * 100.0;
            System.out.printf("Gate %s: baseline=%d ms, actual=%d ms (drift %+.1f%%)%n",
                    name, baseTime, actual, driftPct);
            if (driftPct > threshold) {
                errors.append(String.format(
                        "%s regressed %.1f%% (baseline %d ms vs actual %d ms)%n",
                        name, driftPct, baseTime, actual));
            }
            long maxTime = entry.getValue().get("max_time_ms").asLong(60_000);
            assertTrue(actual < maxTime,
                    name + " exceeded hard ceiling " + maxTime + " ms (was " + actual + ")");
        }
        if (!errors.isEmpty()) {
            fail("Benchmark regression failed:\n" + errors);
        }
    }

    private static long parseCsvTimeMs(File csv) throws IOException {
        for (String line : java.nio.file.Files.readAllLines(csv.toPath())) {
            if (line.startsWith("time_ms,")) {
                return Long.parseLong(line.substring("time_ms,".length()));
            }
        }
        throw new IOException("time_ms line not found in " + csv);
    }
}