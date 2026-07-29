package com.hospital.scheduler.dto.response;

import java.util.List;

/**
 * Typed response for {@code POST /api/v1/staff/import}.
 *
 * <p>Replaces the previous loosely-typed {@code Map<String, Object>} response so
 * that the frontend cannot drift away from the contract.
 */
public record StaffImportResponse(
        int imported,
        int inserted,
        int updated,
        int failed,
        int total,
        List<String> errors,
        String message
) {
    public static StaffImportResponse of(
            int inserted, int updated, int failed, List<String> errors, String message) {
        int imported = inserted + updated;
        int total = imported + failed;
        return new StaffImportResponse(imported, inserted, updated, failed, total, errors, message);
    }
}
