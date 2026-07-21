package com.hospital.scheduler.dto.response;

import com.hospital.scheduler.algorithm.AutoGenConfig;

public record AutoGenConfigRecommendResponse(
    AutoGenConfig recommendedConfig,
    RecommendedRuntimeConfig recommendedRuntimeConfig,
    int totalShiftsExpected,
    int totalStaffTargeted,
    String rationale
) {
    public record RecommendedRuntimeConfig(int maxShiftsPerStaff) {}
}