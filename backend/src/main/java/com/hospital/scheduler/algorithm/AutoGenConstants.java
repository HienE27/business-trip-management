package com.hospital.scheduler.algorithm;

/**
 * Constants for {@link AutoGenConfig} string-literal values.
 *
 * <p>Centralizes all magic strings used by auto-generation logic so that the
 * set of valid values is defined in ONE place. Previously these literals were
 * hardcoded across 11+ files (services, repositories, controllers, schedulers,
 * seeders), which made refactoring risky and grep-based audits noisy.
 *
 * <p><b>Usage:</b> Always reference these constants instead of writing string
 * literals like {@code "SKIP"} or {@code "FAIR_DISTRIBUTE"} directly.
 */
public final class AutoGenConstants {

    private AutoGenConstants() {}

    // ─── Holiday Mode ───────────────────────────────────────────────────────
    /** Skip auto-generation entirely on holidays. */
    public static final String HOLIDAY_MODE_SKIP = "SKIP";
    /** Continue auto-generation on holidays with reduced intensity. */
    public static final String HOLIDAY_MODE_PARTIAL = "PARTIAL";

    // ─── L04 Balance Strategy ───────────────────────────────────────────────
    /** Only assign L04 to staff whose specialty strictly matches the requirement. */
    public static final String BALANCE_STRATEGY_STRICT_MATCH_ONLY = "STRICT_MATCH_ONLY";
    /** Distribute L04 assignments fairly across the eligible pool. */
    public static final String BALANCE_STRATEGY_FAIR_DISTRIBUTE = "FAIR_DISTRIBUTE";
    /**
     * Weighted-fair distribution for L04.
     *
     * @deprecated Not implemented in scheduler v1.0 — no algorithm branch evaluates
     *     this value. The scheduler only distinguishes {@link #BALANCE_STRATEGY_STRICT_MATCH_ONLY}
     *     vs. all other values (treated as fair-distribute). Exposed in the UI as a
     *     dropdown option. Add actual algorithm branching before enabling.
     */
    @Deprecated
    public static final String BALANCE_STRATEGY_WEIGHTED_FAIR = "WEIGHTED_FAIR";
}
