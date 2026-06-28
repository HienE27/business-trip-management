package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * Fitness function and constraint checker for genetic algorithm scheduling.
 * 
 * Constraints:
 * C1: L01 + L02 same staff same day = CONFLICT
 * C2: L03 + L04 same staff same day = CONFLICT
 * C3: Staff cannot work on leave days
 * C4: Staff cannot work on compensation days
 * C5: Only 1 L01 per day (per spec)
 */
@Component
public class SchedulingFitnessFunction {

    private final CompensationDateCalculator compensationDateCalculator;

    public SchedulingFitnessFunction(CompensationDateCalculator compensationDateCalculator) {
        this.compensationDateCalculator = compensationDateCalculator;
    }

    /**
     * Evaluate fitness of a chromosome.
     * Higher fitness = better solution.
     * 
     * Fitness formula:
     * fitness = base - conflictPenalty + balanceBonus + coverageBonus
     * 
     * @param chromosome The chromosome to evaluate
     * @param leaveRequests Approved leave requests
     * @param existingCompensationDays Existing compensation days (staffId_date format)
     * @param excludedStaffIds Staff excluded from scheduling
     * @param config GA configuration
     * @return Fitness score (higher is better)
     */
    public double evaluate(
            ScheduleChromosome chromosome,
            List<LeaveRequest> leaveRequests,
            Set<String> existingCompensationDays,
            Set<Integer> excludedStaffIds,
            GeneticAlgorithmConfig config) {

        // Build lookup maps for fast constraint checking
        Set<String> leaveDays = buildLeaveDaySet(leaveRequests);
        Map<Integer, Set<LocalDate>> staffCompDays = buildCompensationDayMap(existingCompensationDays);
        Map<Integer, Set<LocalDate>> staffLeaves = buildStaffLeaveMap(leaveRequests);
        
        // Calculate conflicts
        int conflicts = countConflicts(chromosome, leaveDays, staffCompDays, staffLeaves, excludedStaffIds);
        
        // Calculate coverage
        double coverage = chromosome.calculateCoverage();
        
        // Calculate balance
        double balance = chromosome.calculateBalance();
        
        // Update chromosome stats
        chromosome.setConflictCount(conflicts);
        chromosome.setCoverageRate(coverage);
        chromosome.setBalanceScore(balance);
        
        // Fitness = base - conflictPenalty + balanceBonus + coverageBonus
        double baseFitness = 1000.0;
        double conflictPenalty = conflicts * config.conflictWeight();
        double balanceBonus = balance * config.balanceWeight();
        double coverageBonus = coverage * config.coverageWeight();
        
        double fitness = baseFitness - conflictPenalty + balanceBonus + coverageBonus;
        chromosome.setFitness(fitness);
        
        return fitness;
    }

    /**
     * Count constraint violations in chromosome.
     */
    public int countConflicts(
            ScheduleChromosome chromosome,
            Set<String> leaveDays,
            Map<Integer, Set<LocalDate>> staffCompDays,
            Map<Integer, Set<LocalDate>> staffLeaves,
            Set<Integer> excludedStaffIds) {

        int conflicts = 0;
        List<ShiftRequirement> requirements = chromosome.getRequirements();
        
        // Group assignments by (staff, date) to check for same-day conflicts
        Map<String, List<String>> staffDateShifts = new HashMap<>();
        
        for (int i = 0; i < requirements.size(); i++) {
            ShiftRequirement req = requirements.get(i);
            Staff staff = chromosome.getStaffAt(i);
            
            if (staff == null) continue;
            if (excludedStaffIds != null && excludedStaffIds.contains(staff.getId())) {
                conflicts++;
                continue;
            }
            
            LocalDate workDate = req.getWorkDate();
            String shiftType = req.getShiftType().getId();
            String key = staff.getId() + "_" + workDate;
            
            // Check leave
            if (staffLeaves.containsKey(staff.getId()) && 
                staffLeaves.get(staff.getId()).contains(workDate)) {
                conflicts++;
                continue;
            }
            
            // Check compensation day
            if (staffCompDays.containsKey(staff.getId()) &&
                staffCompDays.get(staff.getId()).contains(workDate)) {
                conflicts++;
                continue;
            }
            
            // Check holiday
            if (leaveDays.contains(workDate.toString())) {
                conflicts++;
                continue;
            }
            
            // Track for same-day conflict check
            staffDateShifts.computeIfAbsent(key, k -> new ArrayList<>()).add(shiftType);
        }
        
        // Check same-day conflicts: L01 vs L02, L03 vs L04
        for (List<String> shifts : staffDateShifts.values()) {
            boolean hasL01 = shifts.contains(CspConstants.DIRECT_24H);
            boolean hasL02 = shifts.contains(CspConstants.THONG_TAM);
            boolean hasL03 = shifts.contains(CspConstants.DICH_VU);
            boolean hasL04 = shifts.contains(CspConstants.CHUYEN_GIA);
            
            if (hasL01 && hasL02) conflicts++;
            if (hasL03 && hasL04) conflicts++;
        }
        
        // Count unassigned slots as penalty
        conflicts += chromosome.getUnassignedCount();
        
        return conflicts;
    }

    /**
     * Build set of holiday dates.
     */
    private Set<String> buildLeaveDaySet(List<LeaveRequest> leaveRequests) {
        Set<String> holidays = new HashSet<>();
        for (LeaveRequest req : leaveRequests) {
            if (req.getStatus() == LeaveRequest.LeaveStatus.APPROVED) {
                holidays.add(req.getStartDate().toString());
            }
        }
        return holidays;
    }

    /**
     * Build map of staff -> compensation dates.
     */
    private Map<Integer, Set<LocalDate>> buildCompensationDayMap(Set<String> existingCompensationDays) {
        Map<Integer, Set<LocalDate>> map = new HashMap<>();
        for (String comp : existingCompensationDays) {
            String[] parts = comp.split("_");
            if (parts.length >= 2) {
                int staffId = Integer.parseInt(parts[0]);
                LocalDate date = LocalDate.parse(parts[1]);
                map.computeIfAbsent(staffId, k -> new HashSet<>()).add(date);
            }
        }
        return map;
    }

    /**
     * Build map of staff -> leave dates.
     */
    private Map<Integer, Set<LocalDate>> buildStaffLeaveMap(List<LeaveRequest> leaveRequests) {
        Map<Integer, Set<LocalDate>> map = new HashMap<>();
        for (LeaveRequest req : leaveRequests) {
            if (req.getStatus() == LeaveRequest.LeaveStatus.APPROVED) {
                map.computeIfAbsent(req.getStaff().getId(), k -> new HashSet<>())
                        .add(req.getStartDate());
            }
        }
        return map;
    }

    /**
     * Check if a specific assignment is valid.
     */
    public boolean isValidAssignment(Staff staff, LocalDate workDate, String shiftType,
            Set<String> leaveDays, Map<Integer, Set<LocalDate>> staffCompDays,
            Map<Integer, Set<LocalDate>> staffLeaves, Set<Integer> excludedStaffIds) {
        
        if (staff == null || excludedStaffIds.contains(staff.getId())) return false;
        if (leaveDays.contains(workDate.toString())) return false;
        if (staffLeaves.containsKey(staff.getId()) && staffLeaves.get(staff.getId()).contains(workDate)) return false;
        if (staffCompDays.containsKey(staff.getId()) && staffCompDays.get(staff.getId()).contains(workDate)) return false;
        
        return true;
    }

    /**
     * Get valid staff candidates for a requirement.
     */
    public List<Integer> getValidStaffIndices(
            ShiftRequirement req,
            List<Staff> staffPool,
            Set<String> leaveDays,
            Map<Integer, Set<LocalDate>> staffCompDays,
            Map<Integer, Set<LocalDate>> staffLeaves,
            Set<Integer> excludedStaffIds,
            Set<String> alreadyAssignedStaffDateKeys) {
        
        List<Integer> validIndices = new ArrayList<>();
        LocalDate workDate = req.getWorkDate();
        String shiftType = req.getShiftType().getId();
        String dateKey = workDate.toString();
        
        for (int i = 0; i < staffPool.size(); i++) {
            Staff staff = staffPool.get(i);
            
            if (!isValidAssignment(staff, workDate, shiftType, leaveDays, staffCompDays, staffLeaves, excludedStaffIds)) {
                continue;
            }
            
            // Check if staff already assigned to another shift on same day
            String staffDateKey = staff.getId() + "_" + dateKey;
            if (alreadyAssignedStaffDateKeys.contains(staffDateKey)) {
                continue;
            }
            
            // Check same-day conflict: L01 vs L02, L03 vs L04
            // For L01, check if staff already assigned to L02 on same day
            // For L02, check if staff already assigned to L01 on same day
            // etc.
            
            validIndices.add(i);
        }
        
        return validIndices;
    }
}
