package com.hospital.scheduler.scheduling.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable shift requirement representation for the scheduling algorithm.
 * 
 * <p>Represents a single slot that needs to be filled with a staff assignment.</p>
 */
@Getter
@Builder
public final class ShiftRequirementInfo {

    private final int slotId;
    private final LocalDate date;
    private final String shiftTypeId;
    private final Integer specialtyId; // Only for L04
    private final int requiredStaffCount;
    private final int hours;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Integer weekNumber;

    /**
     * Create ShiftRequirementInfo from entity.
     */
    public static ShiftRequirementInfo from(
            com.hospital.scheduler.entity.ShiftRequirement requirement) {
        
        Objects.requireNonNull(requirement, "Requirement cannot be null");
        
        Integer specialtyId = null;
        if (requirement.getSpecialty() != null) {
            specialtyId = requirement.getSpecialty().getId();
        }
        
        return ShiftRequirementInfo.builder()
                .slotId(requirement.getId())
                .date(requirement.getWorkDate())
                .shiftTypeId(requirement.getShiftType().getId())
                .specialtyId(specialtyId)
                .requiredStaffCount(requirement.getRequiredStaffCount())
                .hours(requirement.getHours())
                .startTime(requirement.getStartTime())
                .endTime(requirement.getEndTime())
                .weekNumber(requirement.getWorkDate().get(
                        java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()))
                .build();
    }

    /**
     * Check if this is an L04 requirement.
     */
    public boolean isL04() {
        return "L04".equals(shiftTypeId);
    }

    /**
     * Check if this is a weekend shift.
     */
    public boolean isWeekend() {
        return date.getDayOfWeek().getValue() >= 6;
    }

    @Override
    public String toString() {
        return String.format("Requirement[slot=%d, date=%s, type=%s, specialty=%s, required=%d]",
                slotId, date, shiftTypeId, specialtyId, requiredStaffCount);
    }
}
