package com.hospital.scheduler.algorithm;

import java.time.LocalDate;

/**
 * Simple POJO replacing ShiftRequirement entity for auto-scheduling.
 * Requirements are derived from AutoGenConfig, not from database records.
 *
 * @param shiftTypeId The shift type ID (L01, L02, L03, L04)
 * @param workDate    The work date
 * @param requiredCount Number of staff required for this slot
 * @param specialtyId  The specialty ID for L04 (null for other types)
 */
public record ShiftRequirementInfo(
        String shiftTypeId,
        LocalDate workDate,
        int requiredCount,
        Integer specialtyId
) {
    public ShiftRequirementInfo(String shiftTypeId, LocalDate workDate, int requiredCount) {
        this(shiftTypeId, workDate, requiredCount, null);
    }
}
