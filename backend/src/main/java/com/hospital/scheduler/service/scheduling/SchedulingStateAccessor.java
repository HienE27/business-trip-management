package com.hospital.scheduler.service.scheduling;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * Thread-local state container for in-scheduling-run data.
 * Single instance held by the service; concurrent requests each get their own
 * ThreadLocal storage and cannot see each other's state.
 *
 * <p>All state is reset via {@link #reset()} at the start of each scheduling run.
 */
@Component
public class SchedulingStateAccessor {

    private final ThreadLocal<Map<String, Set<String>>> inMemoryAssignments = ThreadLocal.withInitial(HashMap::new);
    private final ThreadLocal<Set<String>> inMemoryCompensationShiftDates = ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<Set<String>> allCompensationShiftDates = ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<Set<Integer>> swapPriorityStaffIds = ThreadLocal.withInitial(HashSet::new);

    /**
     * Reset all ThreadLocal state. Call at the start of each scheduling run.
     */
    public void reset() {
        inMemoryAssignments.set(new HashMap<>());
        inMemoryCompensationShiftDates.set(new HashSet<>());
        allCompensationShiftDates.set(new HashSet<>());
        swapPriorityStaffIds.set(new HashSet<>());
    }

    /**
     * Clean up ThreadLocal storage. Call in a finally block so the worker thread
     * is left clean after the request completes.
     */
    public void cleanup() {
        inMemoryAssignments.remove();
        inMemoryCompensationShiftDates.remove();
        allCompensationShiftDates.remove();
        swapPriorityStaffIds.remove();
    }

    // ---- In-memory assignments (staffId + date -> set of shiftTypeId) ----

    public Map<String, Set<String>> getInMemoryAssignments() {
        return inMemoryAssignments.get();
    }

    public void addAssignment(int staffId, LocalDate workDate, String shiftTypeId) {
        String key = staffId + "_" + workDate;
        inMemoryAssignments.get().computeIfAbsent(key, k -> new HashSet<>()).add(shiftTypeId);
    }

    public boolean hasInMemoryConflict(int staffId, LocalDate workDate, String shiftTypeId) {
        String key = staffId + "_" + workDate;
        Set<String> existingShifts = inMemoryAssignments.get().get(key);
        if (existingShifts == null) return false;
        for (String existingId : existingShifts) {
            if (existingId.equals(shiftTypeId)) return true;
            if (isBusinessShiftConflict(shiftTypeId, existingId)) return true;
        }
        return false;
    }

    // ---- Compensation shift dates ----

    public Set<String> getInMemoryCompensationShiftDates() {
        return inMemoryCompensationShiftDates.get();
    }

    public Set<String> getAllCompensationShiftDates() {
        return allCompensationShiftDates.get();
    }

    public void addCompensationShiftDate(int staffId, LocalDate date) {
        String compKey = staffId + "_" + date.toString();
        inMemoryCompensationShiftDates.get().add(compKey);
        allCompensationShiftDates.get().add(compKey);
    }

    public void addAllCompensationShiftDate(String compKey) {
        allCompensationShiftDates.get().add(compKey);
    }

    public boolean isCompensationDate(int staffId, LocalDate date) {
        String compKey = staffId + "_" + date.toString();
        return inMemoryCompensationShiftDates.get().contains(compKey)
                || allCompensationShiftDates.get().contains(compKey);
    }

    public void clearAllCompensationShiftDates() {
        allCompensationShiftDates.get().clear();
    }

    // ---- Swap priority ----

    public Set<Integer> getSwapPriorityStaffIds() {
        return swapPriorityStaffIds.get();
    }

    public void addSwapPriorityStaff(int staffId) {
        swapPriorityStaffIds.get().add(staffId);
    }

    public void clearSwapPriorityStaffIds() {
        swapPriorityStaffIds.get().clear();
    }

    // ---- Conflict detection helpers ----

    private boolean isBusinessShiftConflict(String typeA, String typeB) {
        return ("L01".equals(typeA) && "L02".equals(typeB))
                || ("L02".equals(typeA) && "L01".equals(typeB))
                || ("L03".equals(typeA) && "L04".equals(typeB))
                || ("L04".equals(typeA) && "L03".equals(typeB));
    }

    /**
     * Check if assigning L01 on workDate would conflict with adjacent-day L01
     * already in memory (N-1, N-2, or N+1).
     */
    public boolean hasAdjacentL01Conflict(int staffId, LocalDate workDate) {
        Map<String, Set<String>> allAssignments = inMemoryAssignments.get();
        // Check N-1
        if (hasL01On(allAssignments, staffId, workDate.minusDays(1))) return true;
        // Check N-2 (for back-to-back chain)
        if (hasL01On(allAssignments, staffId, workDate.minusDays(2))) return true;
        // Check N+1
        if (hasL01On(allAssignments, staffId, workDate.plusDays(1))) return true;
        return false;
    }

    private boolean hasL01On(Map<String, Set<String>> assignments, int staffId, LocalDate date) {
        Set<String> shifts = assignments.get(staffId + "_" + date);
        return shifts != null && shifts.contains("L01");
    }
}
