package com.hospital.scheduler.scheduling.statistics;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.time.LocalDate;
import java.util.*;

/**
 * Tracks consecutive days worked for each staff member.
 */
public class ConsecutiveStatistics implements StatisticsModule {

    private final SolutionDescriptor descriptor;
    private final IncrementalStatisticsHub hub;
    
    // Map: staffIndex -> (date -> consecutiveDaysUpTo)
    private final Map<Integer, Map<LocalDate, Integer>> consecutiveDays;
    private final Map<Integer, Integer> maxConsecutiveDays;

    public ConsecutiveStatistics(SolutionDescriptor descriptor, IncrementalStatisticsHub hub) {
        this.descriptor = descriptor;
        this.hub = hub;
        this.consecutiveDays = new HashMap<>();
        this.maxConsecutiveDays = new HashMap<>();
    }

    @Override
    public void apply(Move move, WorkingSolution solution) {
        for (int slotId : move.affectedSlotIdsAsList()) {
            MutableAssignment a = solution.getAssignment(slotId);
            if (a != null) {
                int staffIdx = descriptor.getStaffIndex(a.staffId);
                if (staffIdx < 0) continue;
                
                LocalDate date = a.date;
                
                // Calculate consecutive days for this assignment
                int consecutive = 1;
                
                // Check previous day
                Map<LocalDate, Integer> staffCons = consecutiveDays.computeIfAbsent(staffIdx, k -> new HashMap<>());
                Integer prevCons = staffCons.get(date.minusDays(1));
                if (prevCons != null) {
                    consecutive = prevCons + 1;
                }
                
                // Store
                staffCons.put(date, consecutive);
                maxConsecutiveDays.merge(staffIdx, consecutive, Math::max);
            }
        }
    }

    @Override
    public void undo(Move move, WorkingSolution solution) {
        // Full reset is expensive but correct
        reset(solution);
    }

    @Override
    public void reset(WorkingSolution solution) {
        consecutiveDays.clear();
        maxConsecutiveDays.clear();
        
        // Group assignments by staff
        Map<Integer, List<MutableAssignment>> byStaff = new HashMap<>();
        for (MutableAssignment a : solution.getAllAssignments()) {
            byStaff.computeIfAbsent(a.staffId, k -> new ArrayList<>()).add(a);
        }
        
        // Calculate consecutive days for each staff
        for (var entry : byStaff.entrySet()) {
            int staffIdx = descriptor.getStaffIndex(entry.getKey());
            if (staffIdx < 0) continue;
            
            List<MutableAssignment> assignments = entry.getValue();
            assignments.sort(Comparator.comparing(a -> a.date));
            
            Map<LocalDate, Integer> staffCons = new HashMap<>();
            int maxConsec = 0;
            int currentConsec = 0;
            LocalDate prevDate = null;
            
            for (MutableAssignment a : assignments) {
                if (prevDate != null && prevDate.plusDays(1).equals(a.date)) {
                    currentConsec++;
                } else {
                    currentConsec = 1;
                }
                staffCons.put(a.date, currentConsec);
                maxConsec = Math.max(maxConsec, currentConsec);
                prevDate = a.date;
            }
            
            consecutiveDays.put(staffIdx, staffCons);
            maxConsecutiveDays.put(staffIdx, maxConsec);
        }
    }

    /**
     * Get consecutive days for a specific staff and date.
     */
    public int getConsecutiveDays(int staffId, LocalDate date) {
        int staffIdx = descriptor.getStaffIndex(staffId);
        if (staffIdx < 0) return 0;
        
        Map<LocalDate, Integer> staffCons = consecutiveDays.get(staffIdx);
        if (staffCons == null) return 0;
        
        return staffCons.getOrDefault(date, 0);
    }

    /**
     * Get max consecutive days for a staff.
     */
    public int getMaxConsecutive(int staffId) {
        int staffIdx = descriptor.getStaffIndex(staffId);
        if (staffIdx < 0) return 0;
        return maxConsecutiveDays.getOrDefault(staffIdx, 0);
    }

    /**
     * Check if assignment would create too many consecutive days.
     */
    public boolean wouldExceedMaxConsecutive(int staffId, LocalDate date, int maxAllowed) {
        int current = getConsecutiveDays(staffId, date.minusDays(1));
        return current + 1 > maxAllowed;
    }
}
