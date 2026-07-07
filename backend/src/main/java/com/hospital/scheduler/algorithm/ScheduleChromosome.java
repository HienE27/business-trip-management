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
     * Create a random chromosome with semi-balanced initial assignment.
     * Uses round-robin within each shift type to ensure even initial distribution.
     * NOTE: -1 (unassigned) should be avoided during initialization.
     * It will be assigned by mutation/greedy repair when a slot is impossible to fill.
     *
     * Key improvement: Instead of pure random, we distribute assignments evenly
     * across staff for each shift type. This gives the GA a much better starting
     * population and reduces the number of generations needed to reach fairness.
     */
    public static ScheduleChromosome createRandom(
            List<ShiftRequirementInfo> requirements, 
            List<Staff> staffPool,
            Random random) {
        
        ScheduleChromosome chromosome = new ScheduleChromosome(requirements, staffPool);
        
        if (staffPool.isEmpty()) return chromosome;
        
        // Track assignment counts per staff for each shift type (per-specialty for L04)
        // Key: shiftTypeId or "L04:specId", Value: count
        Map<String, int[]> typeCounts = new HashMap<>();
        for (Staff s : staffPool) {
            // Initialize per-type counts
            for (ShiftRequirementInfo req : requirements) {
                String key = getBalanceKey(req);
                typeCounts.computeIfAbsent(key, k -> new int[staffPool.size()]);
            }
        }
        
        // Track total assignments per staff for tiebreaking
        int[] totalCounts = new int[staffPool.size()];
        
        for (int i = 0; i < chromosome.genes.length; i++) {
            ShiftRequirementInfo req = requirements.get(i);
            String balanceKey = getBalanceKey(req);
            int[] counts = typeCounts.get(balanceKey);

            // Find staff with fewest assignments of this type (primary).
            // Among equally-loaded candidates, prefer the one with fewest TOTAL shifts
            // (strong cross-type equity). Without this, staff who already have many
            // L01 can keep absorbing L02/L03/L04 too, creating 8-vs-12 clusters.
            int bestStaff = 0;
            int bestCount = counts != null ? counts[0] : 0;
            int bestTotal = totalCounts[0];

            for (int s = 1; s < staffPool.size(); s++) {
                int typeCount = counts != null ? counts[s] : 0;
                int total = totalCounts[s];

                // Primary: minimize type count. Strong secondary: minimize total.
                if (typeCount < bestCount) {
                    bestStaff = s;
                    bestCount = typeCount;
                    bestTotal = total;
                } else if (typeCount == bestCount) {
                    // Within same type load, prefer staff whose total is below
                    // the population mean to enforce cross-type equity.
                    // Use a weighted comparison: strong penalty on above-mean total.
                    double meanTotal = computeMeanTotal(totalCounts);
                    double thisPenalty = Math.max(0, total - meanTotal);
                    double bestPenalty = Math.max(0, bestTotal - meanTotal);
                    if (thisPenalty < bestPenalty) {
                        bestStaff = s;
                        bestCount = typeCount;
                        bestTotal = total;
                    }
                }
            }

            // Reduce randomness: 80% use best staff (was 70%) — strong balance
            if (random.nextDouble() > 0.80) {
                bestStaff = random.nextInt(staffPool.size());
            }

            chromosome.genes[i] = bestStaff;
            if (counts != null) {
                counts[bestStaff]++;
            }
            totalCounts[bestStaff]++;
        }

        return chromosome;
    }

    /** Compute mean of non-zero totalCounts entries (population average workload). */
    private static double computeMeanTotal(int[] totalCounts) {
        long sum = 0;
        for (int v : totalCounts) sum += v;
        return totalCounts.length == 0 ? 0 : (double) sum / totalCounts.length;
    }

    /**
     * Get the balance key for a requirement, used to group requirements
     * for fairness tracking. For L04, uses per-specialty key.
     */
    private static String getBalanceKey(ShiftRequirementInfo req) {
        if ("L04".equals(req.shiftTypeId()) && req.specialtyId() != null) {
            return "L04:" + req.specialtyId();
        }
        return req.shiftTypeId();
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
    
    public int[] getGenes() {
        return genes;
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

    /**
     * Calculate coverage weighted by shift type.
     * The numerator is the sum of {@code weight[shiftType]} for every
     * actually-assigned slot; the denominator is the same sum over all
     * requirements. Heavy shifts (e.g. L01) thus dominate the coverage
     * signal — a chromosome that fills only the lightest requirements
     * shouldn't score a high coverage even when the slot-count looks
     * good.
     */
    public double calculateWeightedCoverage(Map<String, Double> shiftWeight) {
        double required = 0.0;
        for (ShiftRequirementInfo req : requirements) {
            required += weightOf(shiftWeight, req.shiftTypeId());
        }
        if (required <= 0.0) return 1.0;

        double filled = 0.0;
        for (int i = 0; i < genes.length && i < requirements.size(); i++) {
            if (genes[i] >= 0) {
                filled += weightOf(shiftWeight, requirements.get(i).shiftTypeId());
            }
        }
        return filled / required;
    }

    private static double weightOf(Map<String, Double> weights, String shiftTypeId) {
        Double w = weights.get(shiftTypeId);
        return w != null ? w : 1.0;
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

    /**
     * Per-type balance score (0-1, higher is better).
     * For each shift type that has at least one requirement, compute a
     * balance score (1 - normalised stddev of assignments per staff).
     * Return the average across all active shift types.
     *
     * For L04 (Chuyên gia), we use per-specialty balance: each specialty
     * is balanced independently. This prevents staff from one specialty
     * monopolizing L04 assignments while others get zero.
     */
    public double calculatePerTypeBalance() {
        // Collect per-type, per-staff counts (using composite key for L04 per-specialty)
        Map<String, int[]> typeCounts = new HashMap<>();
        for (int i = 0; i < genes.length && i < requirements.size(); i++) {
            int gene = genes[i];
            if (gene < 0 || gene >= staffPool.size()) continue;
            String shiftType = requirements.get(i).shiftTypeId();
            // For L04, use per-specialty key to ensure independent balance per specialty
            String balanceKey = shiftType;
            if ("L04".equals(shiftType) && requirements.get(i).specialtyId() != null) {
                balanceKey = "L04:" + requirements.get(i).specialtyId();
            }
            typeCounts.computeIfAbsent(balanceKey, k -> new int[staffPool.size()])[gene]++;
        }
        if (typeCounts.isEmpty()) return 1.0;

        double totalScore = 0.0;
        int activeTypes = 0;
        for (Map.Entry<String, int[]> entry : typeCounts.entrySet()) {
            String balanceKey = entry.getKey();
            int[] counts = entry.getValue();
            double total = 0;
            for (int c : counts) total += c;
            if (total == 0) continue;
            // For per-specialty L04: only count staff who have this specialty
            int poolSize;
            if (balanceKey.startsWith("L04:")) {
                // Use full pool - specialty filtering is handled at requirement level
                poolSize = staffPool.size();
            } else {
                poolSize = staffPool.size();
            }
            double mean = total / poolSize;
            if (mean <= 0) { totalScore += 1.0; activeTypes++; continue; }
            double variance = 0;
            for (int c : counts) variance += (c - mean) * (c - mean);
            variance /= poolSize;
            double stdDev = Math.sqrt(variance);
            double score = Math.max(0.0, Math.min(1.0, 1.0 - (stdDev / (mean * 2))));
            totalScore += score;
            activeTypes++;
        }
        return activeTypes > 0 ? totalScore / activeTypes : 1.0;
    }

    /**
     * Weighted-balance variant. Each assignment contributes
     * {@code weight[shiftType]} to the per-staff load; the score rewards
     * chromosomes that distribute heavier shifts (L01) as evenly as
     * possible. Standard deviation of weighted loads is normalised the
     * same way as {@link #calculateBalance()}.
     */
    public double calculateWeightedBalance(Map<String, Double> shiftWeight) {
        if (staffPool.isEmpty()) return 0.0;

        // Per-staff weighted load.
        double[] loads = new double[staffPool.size()];
        for (int i = 0; i < genes.length && i < requirements.size(); i++) {
            int gene = genes[i];
            if (gene < 0) continue;
            if (gene >= loads.length) continue;
            loads[gene] += weightOf(shiftWeight, requirements.get(i).shiftTypeId());
        }

        double total = 0.0;
        for (double load : loads) total += load;
        double mean = total / staffPool.size();
        if (mean <= 0.0) return 1.0;

        double variance = 0.0;
        for (double load : loads) {
            variance += Math.pow(load - mean, 2);
        }
        variance /= staffPool.size();

        double stdDev = Math.sqrt(variance);
        double normalizedScore = 1.0 - (stdDev / (mean * 2));
        return Math.max(0.0, Math.min(1.0, normalizedScore));
    }

    /**
     * Cross-type equity metric. Standard deviation of each staff member's TOTAL
     * shift count against the pool mean. Without this, per-type balance can be
     * perfect while staff total loads diverge wildly.
     */
    public double calculateCrossTypeEquity() {
        if (staffPool.isEmpty()) return 1.0;
        int[] totals = new int[staffPool.size()];
        for (int i = 0; i < genes.length && i < requirements.size(); i++) {
            int gene = genes[i];
            if (gene < 0 || gene >= totals.length) continue;
            totals[gene]++;
        }
        double total = 0;
        for (int t : totals) total += t;
        double mean = total / totals.length;
        if (mean <= 0) return 1.0;
        double variance = 0;
        for (int t : totals) variance += Math.pow(t - mean, 2);
        variance /= totals.length;
        double stdDev = Math.sqrt(variance);
        return Math.max(0.0, Math.min(1.0, 1.0 - (stdDev / (mean * 2))));
    }

    @Override
    public String toString() {
        return String.format("ScheduleChromosome[fitness=%.4f, assignments=%d/%d, conflicts=%d, balance=%.4f]",
                fitness, getAssignmentCount(), getRequiredCount(), conflictCount, balanceScore);
    }
}
