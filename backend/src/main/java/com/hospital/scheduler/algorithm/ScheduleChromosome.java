package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.Staff;
import lombok.Getter;

import java.time.LocalDate;
import java.util.*;

/**
 * Chromosome representation for genetic algorithm scheduling.
 * 
 * Encoding: Each gene represents a slot that needs to be filled.
 * Gene value = index of assigned staff member.
 * 
 * Example:
 * - Gene at position 0 = staff index for requirement[0]
 * - Gene at position 1 = staff index for requirement[1]
 * - etc.
 */
@Getter
public class ScheduleChromosome {
    
    private final int[] genes;  // Staff assignment for each requirement slot
    private final List<ShiftRequirementInfo> requirements;
    private final List<Staff> staffPool;
    
    private double fitness;
    private int conflictCount;
    private double balanceScore;
    private double coverageRate;
    
    public ScheduleChromosome(List<ShiftRequirementInfo> requirements, List<Staff> staffPool) {
        this.requirements = requirements;
        this.staffPool = staffPool;
        this.genes = new int[requirements.size()];
        this.fitness = 0;
        this.conflictCount = 0;
        this.balanceScore = 0;
        this.coverageRate = 0;
    }
    
    /**
     * Create a random chromosome with random staff assignments.
     * NOTE: -1 (unassigned) should be avoided during initialization.
     * It will be assigned by mutation/greedy repair when a slot is impossible to fill.
     */
    public static ScheduleChromosome createRandom(
            List<ShiftRequirementInfo> requirements, 
            List<Staff> staffPool,
            Random random) {
        
        ScheduleChromosome chromosome = new ScheduleChromosome(requirements, staffPool);
        
        for (int i = 0; i < chromosome.genes.length; i++) {
            // Always assign a valid staff during initialization to maximize coverage
            // -1 should only be assigned by repair/greedy when a slot is impossible
            chromosome.genes[i] = random.nextInt(staffPool.size());
        }
        
        return chromosome;
    }
    
    /**
     * Create a deep copy of this chromosome.
     */
    public ScheduleChromosome copy() {
        ScheduleChromosome copy = new ScheduleChromosome(requirements, staffPool);
        System.arraycopy(this.genes, 0, copy.genes, 0, this.genes.length);
        copy.fitness = this.fitness;
        copy.conflictCount = this.conflictCount;
        copy.balanceScore = this.balanceScore;
        copy.coverageRate = this.coverageRate;
        return copy;
    }
    
    /**
     * Get staff assigned to a requirement at given index.
     * Returns null if unassigned (gene = -1).
     */
    public Staff getStaffAt(int requirementIndex) {
        int staffIndex = genes[requirementIndex];
        if (staffIndex < 0 || staffIndex >= staffPool.size()) {
            return null;
        }
        return staffPool.get(staffIndex);
    }
    
    /**
     * Set staff assignment for a requirement.
     */
    public void setStaffAt(int requirementIndex, int staffIndex) {
        genes[requirementIndex] = staffIndex;
    }
    
    /**
     * Get total number of assignments.
     */
    public int getAssignmentCount() {
        int count = 0;
        for (int gene : genes) {
            if (gene >= 0) count++;
        }
        return count;
    }
    
    /**
     * Get total required staff count.
     */
    public int getRequiredCount() {
        return requirements.stream()
                .mapToInt(ShiftRequirementInfo::requiredCount)
                .sum();
    }
    
    /**
     * Get number of unassigned slots (requirement slots without staff assignment).
     * Note: This counts slots (-1 values), not the total staff required.
     */
    public int getUnassignedCount() {
        return requirements.size() - getAssignmentCount();
    }
    
    /**
     * Calculate coverage rate (percentage of requirements fulfilled).
     * Uses requirements.size() to match Greedy/Backtracking behavior.
     */
    public double calculateCoverage() {
        int required = requirements.size(); // Number of requirement slots to fill
        if (required == 0) return 1.0;
        return (double) getAssignmentCount() / required;
    }
    
    // Setters for GA use
    public void setConflictCount(int count) { this.conflictCount = count; }
    public void setCoverageRate(double rate) { this.coverageRate = rate; }
    public void setBalanceScore(double score) { this.balanceScore = score; }
    public void setFitness(double fitness) { this.fitness = fitness; }
    
    /**
     * Count how many times each staff is assigned.
     */
    public Map<Integer, Integer> getStaffAssignmentCounts() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int gene : genes) {
            if (gene >= 0) {
                counts.merge(gene, 1, Integer::sum);
            }
        }
        return counts;
    }
    
    /**
     * Calculate balance score (0-1, higher is better).
     * Uses standard deviation of assignments per staff.
     */
    public double calculateBalance() {
        Map<Integer, Integer> counts = getStaffAssignmentCounts();
        if (counts.isEmpty()) return 0.0;
        
        double mean = (double) getAssignmentCount() / staffPool.size();
        double variance = 0.0;
        
        for (Staff staff : staffPool) {
            int count = counts.getOrDefault(staffPool.indexOf(staff), 0);
            variance += Math.pow(count - mean, 2);
        }
        variance /= staffPool.size();
        
        double stdDev = Math.sqrt(variance);
        // Convert to 0-1 score (lower std dev = higher score)
        // Assume max acceptable std dev is mean (complete imbalance)
        if (mean == 0) return 1.0;
        double normalizedScore = 1.0 - (stdDev / (mean * 2));
        return Math.max(0.0, Math.min(1.0, normalizedScore));
    }
    
    @Override
    public String toString() {
        return String.format("ScheduleChromosome[fitness=%.4f, assignments=%d/%d, conflicts=%d, balance=%.4f]",
                fitness, getAssignmentCount(), getRequiredCount(), conflictCount, balanceScore);
    }
}
