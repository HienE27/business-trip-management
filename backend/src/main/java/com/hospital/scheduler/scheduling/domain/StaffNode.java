package com.hospital.scheduler.scheduling.domain;

import com.hospital.scheduler.entity.Staff;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

/**
 * Immutable, algorithm-friendly view of a staff member.
 *
 * <p>Mirrors the {@link Staff} entity but drops JPA concerns and pre-computes
 * derived data (eligible shift types) once at construction time so the search
 * loop can avoid repeat eligibility checks.
 */
@Getter
public final class StaffNode {

    /** Database primary key (Integer in JPA, primitive int here for speed). */
    private final int id;

    /** Display name (used for logging/telemetry only). */
    private final String fullName;

    /**
     * Set of shift-type IDs the staff is eligible for (L01..L04).
     * Built once via {@link #determineEligibleTypes(Staff)}.
     */
    private final Set<String> eligibleShiftTypes;

    private final Integer maxHoursPerWeek;
    private final Integer maxShiftsPerMonth;
    private final Integer specialtyId;
    private final boolean prefersWeekends;

    private StaffNode(int id,
                      String fullName,
                      Set<String> eligibleShiftTypes,
                      Integer maxHoursPerWeek,
                      Integer maxShiftsPerMonth,
                      Integer specialtyId,
                      boolean prefersWeekends) {
        this.id = id;
        this.fullName = fullName;
        this.eligibleShiftTypes = eligibleShiftTypes;
        this.maxHoursPerWeek = maxHoursPerWeek;
        this.maxShiftsPerMonth = maxShiftsPerMonth;
        this.specialtyId = specialtyId;
        this.prefersWeekends = prefersWeekends;
    }

    /**
     * Convert a JPA {@link Staff} entity to an algorithm-friendly {@code StaffNode}.
     *
     * <p>Eligibility is determined by:
     * <ol>
     *   <li>Staff has an active role in the system ({@code isActive = true})</li>
     *   <li>Staff has a specialty (cross-specialty rules live in the
     *       {@code StaffEligibilityFilter}, which is queried separately via
     *       {@link SchedulingProblem#getEligibleStaff(int)})</li>
     * </ol>
     *
     * <p>For v10, all active staff are eligible for L01..L03; L04 stays
     * specialty-restricted unless cross-specialty is enabled elsewhere.
     */
    public static StaffNode from(Staff staff) {
        if (staff == null) {
            throw new IllegalArgumentException("staff must not be null");
        }
        Integer id = staff.getId();
        if (id == null) {
            throw new IllegalArgumentException("staff.id must not be null");
        }
        return new StaffNode(
                id,
                staff.getFullName(),
                determineEligibleTypes(staff),
                null, // maxHoursPerWeek — not in entity schema, can be added later
                staff.getMaxShiftsPerMonth(),
                staff.getSpecialty() != null ? staff.getSpecialty().getId() : null,
                false // prefersWeekends — entity doesn't expose this yet
        );
    }

    /**
     * Default eligibility policy for v10 — all active staff can take any shift
     * type at the v10 layer. Specialty-based restrictions are layered on top
     * via {@code StaffEligibilityFilter} which the caller invokes when
     * computing {@code getEligibleStaff(slotId)}.
     */
    private static Set<String> determineEligibleTypes(Staff staff) {
        Set<String> types = new HashSet<>();
        if (Boolean.TRUE.equals(staff.getIsActive())) {
            types.add("L01");
            types.add("L02");
            types.add("L03");
            types.add("L04");
        }
        return types;
    }

    /** Returns true if {@code shiftTypeId} is in {@link #eligibleShiftTypes}. */
    public boolean isEligibleFor(String shiftTypeId) {
        return eligibleShiftTypes.contains(shiftTypeId);
    }
}