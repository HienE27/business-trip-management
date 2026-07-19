package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.service.ConflictDetectionService;

import java.time.LocalDate;
import java.util.List;

/**
 * Shared helpers for schedulers. Keeps cross-algorithm logic (conflict checks,
 * requirement/ShiftType lookups) in one place so future algorithm variants
 * don't reimplement the same rules.
 */
public final class ScheduleConflictUtils {

    private ScheduleConflictUtils() {}

    /** L01↔L02 and L03↔L04 same-day conflict (the only business constraints). */
    public static boolean isBusinessConflict(String a, String b) {
        return (ConflictDetectionService.SHIFT_TYPE_L01.equals(a) && ConflictDetectionService.SHIFT_TYPE_L02.equals(b))
            || (ConflictDetectionService.SHIFT_TYPE_L02.equals(a) && ConflictDetectionService.SHIFT_TYPE_L01.equals(b))
            || (ConflictDetectionService.SHIFT_TYPE_L03.equals(a) && ConflictDetectionService.SHIFT_TYPE_L04.equals(b))
            || (ConflictDetectionService.SHIFT_TYPE_L04.equals(a) && ConflictDetectionService.SHIFT_TYPE_L03.equals(b));
    }

    /** First {@link ShiftType} whose id matches; null if none. */
    public static ShiftType findShiftType(String id, List<ShiftRequirement> reqs) {
        return reqs.stream()
                .filter(r -> r.getShiftType().getId().equals(id))
                .findFirst()
                .map(ShiftRequirement::getShiftType)
                .orElse(null);
    }

    /**
     * Resolve the concrete requirement a schedule should reference for a
     * (date, shiftType) pair. When several requirements exist for the same
     * slot (e.g. L04 with different specialties), prefer the one matching
     * the staff's specialty. Returns null if no requirement matches.
     */
    public static ShiftRequirement findMatchingRequirement(
            Staff staff, LocalDate workDate, String shiftTypeId, List<ShiftRequirement> reqs) {
        List<ShiftRequirement> candidates = reqs.stream()
                .filter(r -> r.getShiftType().getId().equals(shiftTypeId)
                        && r.getWorkDate().equals(workDate))
                .toList();
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);
        Specialty sp = staff != null ? staff.getSpecialty() : null;
        if (sp != null) {
            for (ShiftRequirement r : candidates) {
                if (r.getSpecialty() != null && r.getSpecialty().getId().equals(sp.getId())) {
                    return r;
                }
            }
        }
        return candidates.get(0);
    }
}