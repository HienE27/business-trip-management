package com.hospital.scheduler.dto.response;

import com.hospital.scheduler.algorithm.AutoGenConfig;

public record AutoGenConfigRecommendResponse(
    AutoGenConfig recommendedConfig,
    int totalShiftsExpected,
    int totalStaffTargeted,
    String rationale
) {}