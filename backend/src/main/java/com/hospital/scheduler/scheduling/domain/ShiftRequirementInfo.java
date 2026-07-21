package com.hospital.scheduler.scheduling.domain;

import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.ShiftType;

import java.time.LocalDate;

/**
 * Immutable, algorithm-friendly view of one {@code ShiftRequirement} row.
 *
 * <p>This is the v10 equivalent of {@code com.hospital.scheduler.algorithm.ShiftRequirementInfo}.
 * Kept in a separate package so the v10 layer can evolve independently.
 */
public record ShiftRequirementInfo(
        int id,
        LocalDate date,
        String shiftTypeId,
        Integer specialtyId,
        int requiredStaffCount
) {

    /**
     * Convert a JPA {@link ShiftRequirement} entity.
     *
     * <p>Defensive against null fields (DB rows mid-update) — substitutes
     * safe defaults so the search loop never NPEs.
     */
    public static ShiftRequirementInfo from(ShiftRequirement sr) {
        if (sr == null) {
            throw new IllegalArgumentException("ShiftRequirement must not be null");
        }
        Integer id = sr.getId();
        if (id == null) {
            throw new IllegalArgumentException("ShiftRequirement.id must not be null");
        }
        ShiftType st = sr.getShiftType();
        String shiftTypeId = st != null ? st.getId() : null;
        Integer specialtyId = sr.getSpecialty() != null ? sr.getSpecialty().getId() : null;
        int required = sr.getRequiredStaffCount() != null ? sr.getRequiredStaffCount() : 0;
        return new ShiftRequirementInfo(
                id,
                sr.getWorkDate(),
                shiftTypeId,
                specialtyId,
                required);
    }
}