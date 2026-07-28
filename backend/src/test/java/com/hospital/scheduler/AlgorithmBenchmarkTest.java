package com.hospital.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Real-data benchmark: CSP-MRV-FC vs V10 (Tabu+Sampled).
 * <p>
 * Requires MySQL Docker on localhost:3306 with seeded data.
 * Disabled by default — run manually with {@code -Dspring.test.context.cache.maxSize=1}
 * and {@code -Dtest="AlgorithmBenchmarkTest"} when MySQL is available.
 */
@Disabled("Requires MySQL Docker + sufficient heap — run manually")
@Slf4j
@DisplayName("Real-data benchmark: CSP-MRV-FC vs V10 (Tabu+Sampled)")
class AlgorithmBenchmarkTest {

    @Test
    @DisplayName("disabled — placeholder")
    void placeholder() {
        // placeholder to keep JUnit happy
    }
}
