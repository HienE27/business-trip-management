package com.hospital.scheduler.scheduling.config.dto;

/**
 * One row of a profile diff. {@code oldValue} / {@code newValue} are
 * stringified; the frontend is responsible for type-aware rendering.
 */
public record DiffEntryDto(
        String fieldPath,
        String oldValue,
        String newValue
) {
}