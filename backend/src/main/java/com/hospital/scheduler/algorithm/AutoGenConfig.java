package com.hospital.scheduler.algorithm;

import lombok.Builder;

/**
 * Configuration for automatic generation of shift requirements.
 * Used by M07 Auto-Scheduling to generate requirements for all days in a period.
 */
@Builder
public record AutoGenConfig(
    boolean enabled,
    int l01RequiredPerDay,
    int l02RequiredPerDay,
    int l03RequiredPerDay,
    int l04RequiredPerDay,
    int minL01PerWeek,
    int minL02PerWeek,
    int minL03PerWeek,
    int minL04PerWeek,
    String holidayMode  // "SKIP" or "PARTIAL"
) {
    public static AutoGenConfig disabled() {
        return new AutoGenConfig(false, 2, 2, 2, 2, 1, 3, 2, 1, "SKIP");
    }
}
