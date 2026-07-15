package com.hospital.scheduler.scheduling.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable assignment representation for the scheduling algorithm.
 * 
 * <p>Represents a single staff-to-slot assignment with all necessary metadata.</p>
 */
@Getter
@Builder
public final class AssignmentNode {

    private final int slotId;
    private final int staffId;
    private final LocalDate date;
    private final String shiftTypeId;
    private final int hours;
    private final boolean isWeekend;
    private final boolean isHoliday;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Integer weekNumber;

    /**
     * Create from slot requirement and staff assignment.
     */
    public static AssignmentNode from(ShiftRequirementInfo requirement, int staffId) {
        return AssignmentNode.builder()
                .slotId(requirement.getSlotId())
                .staffId(staffId)
                .date(requirement.getDate())
                .shiftTypeId(requirement.getShiftTypeId())
                .hours(requirement.getHours())
                .isWeekend(isWeekendDay(requirement.getDate()))
                .isHoliday(false) // Will be set by caller if needed
                .startTime(requirement.getStartTime())
                .endTime(requirement.getEndTime())
                .weekNumber(getWeekNumber(requirement.getDate()))
                .build();
    }

    private static boolean isWeekendDay(LocalDate date) {
        return date.getDayOfWeek().getValue() >= 6; // Saturday = 6, Sunday = 7
    }

    private static Integer getWeekNumber(LocalDate date) {
        return date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
    }

    /**
     * Create a copy with modified staff.
     */
    public AssignmentNode withStaffId(int newStaffId) {
        return AssignmentNode.builder()
                .slotId(this.slotId)
                .staffId(newStaffId)
                .date(this.date)
                .shiftTypeId(this.shiftTypeId)
                .hours(this.hours)
                .isWeekend(this.isWeekend)
                .isHoliday(this.isHoliday)
                .startTime(this.startTime)
                .endTime(this.endTime)
                .weekNumber(this.weekNumber)
                .build();
    }

    /**
     * Create a copy with holiday flag.
     */
    public AssignmentNode withHoliday(boolean holiday) {
        return AssignmentNode.builder()
                .slotId(this.slotId)
                .staffId(this.staffId)
                .date(this.date)
                .shiftTypeId(this.shiftTypeId)
                .hours(this.hours)
                .isWeekend(this.isWeekend)
                .isHoliday(holiday)
                .startTime(this.startTime)
                .endTime(this.endTime)
                .weekNumber(this.weekNumber)
                .build();
    }

    @Override
    public String toString() {
        return String.format("Assignment[slot=%d, staff=%d, date=%s, type=%s]",
                slotId, staffId, date, shiftTypeId);
    }
}
