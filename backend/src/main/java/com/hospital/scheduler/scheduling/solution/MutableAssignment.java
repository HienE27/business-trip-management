package com.hospital.scheduler.scheduling.solution;

import com.hospital.scheduler.scheduling.domain.AssignmentNode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mutable counterpart of {@link AssignmentNode} — used by the search loop
 * to avoid allocating a new record on every move. Fields are public so the
 * loop can update in place without going through setters.
 *
 * <p>{@link #weekCache} memoizes the ISO 8601 week-of-year since the search
 * loop reads it for every statistics update.
 */
public final class MutableAssignment {

    public int slotId;
    public int staffId;
    public LocalDate date;
    public String shiftTypeId;
    public int hours;
    public boolean isWeekend;
    public boolean isHoliday;
    public LocalDateTime startTime;
    public LocalDateTime endTime;

    /** Lazy memoization sentinel — recomputed on first call after {@link #resetWeekCache()}. */
    private int weekCache = Integer.MIN_VALUE;

    public MutableAssignment() {
        // no-arg constructor for direct mutation by the search loop
    }

    /** Copy constructor — used when promoting {@link AssignmentNode} into the working set. */
    public MutableAssignment(AssignmentNode node) {
        this.slotId = node.slotId();
        this.staffId = node.staffId();
        this.date = node.date();
        this.shiftTypeId = node.shiftTypeId();
        this.hours = node.hours();
        this.isWeekend = node.isWeekend();
        this.isHoliday = node.isHoliday();
        this.startTime = node.startTime();
        this.endTime = node.endTime();
    }

    /** Build a fresh {@link MutableAssignment} from an {@link AssignmentNode}. */
    public static MutableAssignment from(AssignmentNode node) {
        return new MutableAssignment(node);
    }

    /** Compute and cache the ISO 8601 week-of-year for {@link #date}. */
    public int getWeek() {
        if (weekCache == Integer.MIN_VALUE && date != null) {
            weekCache = date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        }
        return weekCache;
    }

    /** Invalidate the cached week — call after mutating {@link #date}. */
    public void resetWeekCache() {
        weekCache = Integer.MIN_VALUE;
    }

    /** Snapshot back to an immutable {@link AssignmentNode}. */
    public AssignmentNode toImmutable() {
        return new AssignmentNode(
                slotId, staffId, date, shiftTypeId, hours,
                isWeekend, isHoliday, startTime, endTime,
                date != null ? getWeek() : null);
    }
}