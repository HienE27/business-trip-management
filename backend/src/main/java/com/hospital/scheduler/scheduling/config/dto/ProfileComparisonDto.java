package com.hospital.scheduler.scheduling.config.dto;

import java.util.List;

/**
 * Result of comparing two profiles (or a profile against the active config).
 *
 * <p>{@code profileA} is non-null when both inputs are profiles. When comparing
 * a profile against the live config, {@code profileA} is null and the frontend
 * should display the "current config" baseline accordingly.
 */
public record ProfileComparisonDto(
        ConfigProfileDto profileA,
        ConfigProfileDto profileB,
        List<DiffEntryDto> differences
) {
}