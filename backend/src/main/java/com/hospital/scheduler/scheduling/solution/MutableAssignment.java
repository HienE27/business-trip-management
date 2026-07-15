package com.hospital.scheduler.scheduling.solution;

import com.hospital.scheduler.scheduling.domain.AssignmentNode;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mutable assignment entity for efficient in-place updates.
 * 
 * <p>Unlike immutable AssignmentNode, this class allows modification of the staffId
 * field to support the apply/undo pattern without object allocation.</p>
 */
@Getter
public class MutableAssignment {

    public int slotId;
    public int staffId;
    public LocalDate date;
    public String shiftTypeId;
    public int hours;
    public boolean isWeekend;
    public boolean isHoliday;
    public LocalDateTime startTime;
    public LocalDateTime endTime;
    private int weekCache = Integer.MIN_VALUE;

    public MutableAssignment() {
    }

    /**
     * Create from immutable AssignmentNode.
     */
    public static MutableAssignment from(AssignmentNode node) {
        MutableAssignment a = new MutableAssignment();
        a.slotId = node.getSlotId();
        a.staffId = node.getStaffId();
        a.date = node.getDate();
        a.shiftTypeId = node.getShiftTypeId();
        a.hours = node.getHours();
        a.isWeekend = node.isWeekend();
        a.isHoliday = node.isHoliday();
        a.startTime = node.getStartTime();
        a.endTime = node.getEndTime();
        return a;
    }

    /**
     * Create a new mutable assignment.
     */
    public static MutableAssignment create(int slotId, int staffId, LocalDate date,
            String shiftTypeId, int hours, boolean isHoliday) {
        MutableAssignment a = new MutableAssignment();
        a.slotId = slotId;
        a.staffId = staffId;
        a.date = date;
        a.shiftTypeId = shiftTypeId;
        a.hours = hours;
        a.isWeekend = isWeekendDay(date);
        a.isHoliday = isHoliday;
        return a;
    }

    /**
     * Get week number (cached).
     */
    public int getWeek() {
        if (weekCache == Integer.MIN_VALUE) {
            weekCache = date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        }
        return weekCache;
    }

    /**
     * Convert to immutable AssignmentNode.
     */
    public AssignmentNode toImmutable() {
        return AssignmentNode.builder()
                .slotId(slotId)
                .staffId(staffId)
                .date(date)
                .shiftTypeId(shiftTypeId)
                .hours(hours)
                .isWeekend(isWeekend)
                .isHoliday(isHoliday)
                .startTime(startTime)
                .endTime(endTime)
                .weekNumber(weekCache != Integer.MIN_VALUE ? weekCache : getWeek())
                .build();
    }

    /**
     * Copy this assignment.
     */
    public MutableAssignment copy() {
        MutableAssignment c = new MutableAssignment();
        c.slotId = this.slotId;
        c.staffId = this.staffId;
        c.date = this.date;
        c.shiftTypeId = this.shiftTypeId;
        c.hours = this.hours;
        c.isWeekend = this.isWeekend;
        c.isHoliday = this.isHoliday;
        c.startTime = this.startTime;
        c.endTime = this.endTime;
        c.weekCache = this.weekCache;
        return c;
    }

    /**
     * Check if this is a weekend assignment.
     */
    public boolean isWeekendDay(LocalDate date) {
        return date.getDayOfWeek().getValue() >= 6;
    }

    /**
     * Check if this is an L01 assignment.
     */
    public boolean isL01() {
        return "L01".equals(shiftTypeId);
    }

    /**
     * Check if this is an L04 assignment.
     */
    public boolean isL04() {
        return "L04".equals(shiftTypeId);
    }

    @Override
    public String toString() {
        return String.format("MutableAssignment[slot=%d, staff=%d, date=%s, type=%s]",
                slotId, staffId, date, shiftTypeId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MutableAssignment that = (MutableAssignment) o;
        return slotId == that.slotId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(slotId);
    }
}
