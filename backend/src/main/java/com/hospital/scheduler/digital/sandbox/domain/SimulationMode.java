package com.hospital.scheduler.digital.sandbox.domain;

/**
 * Simulation mode determines how the sandbox scheduler behaves.
 */
public enum SimulationMode {
    /** Single run with current profile. */
    SINGLE_RUN,

    /** Compare two profiles. */
    COMPARE,

    /** Parameter sensitivity analysis. */
    SENSITIVITY,

    /** Batch what-if scenarios. */
    WHAT_IF,

    /** Generate heatmap/visualization. */
    VISUALIZATION
}
