package com.hospital.scheduler.scheduling.domain;

import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.Specialty;
import lombok.Builder;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

/**
 * Immutable staff representation for the scheduling algorithm.
 * 
 * <p>Replaces entity Staff for use within the algorithm layer,
 * separating business logic from persistence concerns.</p>
 */
@Getter
@Builder
public final class StaffNode {

    private final int id;
    private final String fullName;
    private final Integer specialtyId;
    private final String specialtyName;
    private final Set<String> eligibleShiftTypes;
    private final Integer maxHoursPerWeek;
    private final Integer maxShiftsPerMonth;
    private final boolean prefersWeekends;
    private final boolean isActive;

    /**
     * Create StaffNode from entity.
     */
    public static StaffNode from(Staff staff) {
        if (staff == null) {
            throw new IllegalArgumentException("Staff cannot be null");
        }

        Set<String> eligibleTypes = determineEligibleTypes(staff);

        String specialtyName = null;
        Integer specialtyId = null;
        Specialty specialty = staff.getSpecialty();
        if (specialty != null) {
            specialtyId = specialty.getId();
            specialtyName = specialty.getName();
        }

        return StaffNode.builder()
                .id(staff.getId())
                .fullName(staff.getFullName())
                .specialtyId(specialtyId)
                .specialtyName(specialtyName)
                .eligibleShiftTypes(eligibleTypes)
                .maxHoursPerWeek(staff.getMaxHoursPerWeek())
                .maxShiftsPerMonth(staff.getMaxShiftsPerMonth())
                .prefersWeekends(staff.getPrefersWeekends() != null && staff.getPrefersWeekends())
                .isActive(Boolean.TRUE.equals(staff.getIsActive()))
                .build();
    }

    /**
     * Determine eligible shift types for this staff based on specialty.
     */
    private static Set<String> determineEligibleTypes(Staff staff) {
        Set<String> types = new HashSet<>();

        Specialty specialty = staff.getSpecialty();
        if (specialty == null || !Boolean.TRUE.equals(staff.getIsActive())) {
            return types;
        }

        String specialtyName = specialty.getName();

        // Core specialties: Ngoại, Nội - eligible for L01, L02, L03, L04
        if (Set.of("Ngoại", "Nội").contains(specialtyName)) {
            types.add("L01");
            types.add("L02");
            types.add("L03");
            types.add("L04");
        }
        // Extended specialties for L04 only
        else if (Set.of("Sản", "Nhi", "Mắt", "Răng").contains(specialtyName)) {
            types.add("L04");
        }

        return types;
    }

    /**
     * Check if this staff is eligible for a specific shift type.
     */
    public boolean isEligibleFor(String shiftTypeId) {
        return eligibleShiftTypes != null && eligibleShiftTypes.contains(shiftTypeId);
    }

    /**
     * Check if this staff is eligible for a specific shift type and specialty.
     */
    public boolean isEligibleFor(String shiftTypeId, Integer requiredSpecialtyId) {
        if (!isEligibleFor(shiftTypeId)) {
            return false;
        }

        // L04 requires matching specialty
        if ("L04".equals(shiftTypeId) && requiredSpecialtyId != null) {
            return specialtyId != null && specialtyId.equals(requiredSpecialtyId);
        }

        return true;
    }
}
