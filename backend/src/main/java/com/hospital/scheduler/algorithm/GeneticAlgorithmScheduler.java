package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Staff;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Genetic Algorithm implementation for hospital shift scheduling.
 * 
 * Features:
 * - Tournament selection
 * - Order crossover (OX) for permutation representation
 * - Swap mutation
 * - Elitism to preserve best solutions
 * - Constraint-aware fitness evaluation
 */
@Slf4j
@Component
public class GeneticAlgorithmScheduler implements SchedulingAlgorithm {

    private final SchedulingFitnessFunction fitnessFunction;
    private final Random random;
    
    public GeneticAlgorithmScheduler(SchedulingFitnessFunction fitnessFunction) {
        this.fitnessFunction = fitnessFunction;
        this.random = new Random();
    }

    @Override
    public String getName() {
        return "GENETIC";
    }

    @Override
    public String getDescription() {
        return "Thuật toán di truyền - Tìm nghiệm tối ưu bằng tiến hóa quần thể";
    }

    @Override
    public SchedulingResult solve(
            List<Staff> staffList,
            LocalDate startDate,
            LocalDate endDate,
            List<ShiftRequirementInfo> requirements,
            Set<String> existingCompensationDays,
            List<LeaveRequest> leaveRequests,
            Set<Integer> excludedStaffIds) {
        
        long startTime = System.currentTimeMillis();
        
        // Build config
        GeneticAlgorithmConfig config = GeneticAlgorithmConfig.DEFAULT;
        
        // Filter active staff
        List<Staff> activeStaff = staffList.stream()
                .filter(Staff::getIsActive)
                .filter(s -> excludedStaffIds == null || !excludedStaffIds.contains(s.getId()))
                .toList();
        
        if (activeStaff.isEmpty()) {
            return SchedulingResult.builder()
                    .valid(false)
                    .errors(List.of("Không có nhân sự nào hoạt động"))
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
        
        // Initialize population
        List<ScheduleChromosome> population = initializePopulation(requirements, activeStaff, config);
        
        // Evaluate initial population
        for (ScheduleChromosome chromosome : population) {
            evaluate(chromosome, leaveRequests, existingCompensationDays, excludedStaffIds, config);
        }
        
        ScheduleChromosome bestSolution = null;
        int generationsWithoutImprovement = 0;
        int maxGenerationsWithoutImprovement = 50;
        
        // Evolution loop
        for (int gen = 0; gen < config.maxGenerations(); gen++) {
            // Check time limit
            if (System.currentTimeMillis() - startTime > config.timeLimitMs()) {
                log.info("GA: Time limit reached at generation {}", gen);
                break;
            }
            
            // Sort by fitness (descending)
            population.sort((a, b) -> Double.compare(b.getFitness(), a.getFitness()));
            
            // Track best solution
            if (bestSolution == null || population.get(0).getFitness() > bestSolution.getFitness()) {
                bestSolution = population.get(0).copy();
                generationsWithoutImprovement = 0;
            } else {
                generationsWithoutImprovement++;
            }
            
            // Early termination if no improvement for many generations
            if (generationsWithoutImprovement >= maxGenerationsWithoutImprovement) {
                log.info("GA: Early termination at generation {} (no improvement for {} generations)", 
                        gen, maxGenerationsWithoutImprovement);
                break;
            }
            
            // Log progress every 50 generations
            if (gen % 50 == 0) {
                log.info("GA Generation {}: best={}, avg={}, conflicts={}", 
                        gen, 
                        String.format("%.2f", bestSolution.getFitness()),
                        String.format("%.2f", population.stream().mapToDouble(ScheduleChromosome::getFitness).average().orElse(0)),
                        bestSolution.getConflictCount());
            }
            
            // Create next generation
            List<ScheduleChromosome> nextGeneration = new ArrayList<>();
            
            // Elitism: keep best chromosomes
            int eliteCount = config.eliteCount();
            for (int i = 0; i < eliteCount; i++) {
                nextGeneration.add(population.get(i).copy());
            }
            
            // Fill rest with offspring
            while (nextGeneration.size() < config.populationSize()) {
                // Tournament selection
                ScheduleChromosome parent1 = tournamentSelection(population, config.tournamentSize());
                ScheduleChromosome parent2 = tournamentSelection(population, config.tournamentSize());
                
                // Crossover
                ScheduleChromosome child1, child2;
                if (random.nextDouble() < config.crossoverRate()) {
                    ScheduleChromosome[] children = crossover(parent1, parent2, requirements, activeStaff);
                    child1 = children[0];
                    child2 = children[1];
                } else {
                    child1 = parent1.copy();
                    child2 = parent2.copy();
                }
                
                // Mutation
                if (random.nextDouble() < config.mutationRate()) {
                    mutate(child1, requirements, activeStaff, leaveRequests, existingCompensationDays, 
                           excludedStaffIds, config);
                }
                if (random.nextDouble() < config.mutationRate()) {
                    mutate(child2, requirements, activeStaff, leaveRequests, existingCompensationDays,
                           excludedStaffIds, config);
                }
                
                // Evaluate children
                evaluate(child1, leaveRequests, existingCompensationDays, excludedStaffIds, config);
                evaluate(child2, leaveRequests, existingCompensationDays, excludedStaffIds, config);
                
                nextGeneration.add(child1);
                if (nextGeneration.size() < config.populationSize()) {
                    nextGeneration.add(child2);
                }
            }
            
            population = nextGeneration;
        }
        
        // Final evaluation
        if (bestSolution == null && !population.isEmpty()) {
            population.sort((a, b) -> Double.compare(b.getFitness(), a.getFitness()));
            bestSolution = population.get(0);
        }
        
        // Build result
        return buildResult(bestSolution, requirements, activeStaff, startTime);
    }

    /**
     * Initialize population with random chromosomes.
     */
    private List<ScheduleChromosome> initializePopulation(
            List<ShiftRequirementInfo> requirements,
            List<Staff> staffPool,
            GeneticAlgorithmConfig config) {
        
        List<ScheduleChromosome> population = new ArrayList<>();
        
        for (int i = 0; i < config.populationSize(); i++) {
            ScheduleChromosome chromosome = ScheduleChromosome.createRandom(requirements, staffPool, random);
            population.add(chromosome);
        }
        
        return population;
    }

    /**
     * Evaluate chromosome fitness.
     */
    private void evaluate(
            ScheduleChromosome chromosome,
            List<LeaveRequest> leaveRequests,
            Set<String> existingCompensationDays,
            Set<Integer> excludedStaffIds,
            GeneticAlgorithmConfig config) {
        
        fitnessFunction.evaluate(chromosome, leaveRequests, existingCompensationDays, excludedStaffIds, config);
    }

    /**
     * Tournament selection for parent selection.
     */
    private ScheduleChromosome tournamentSelection(List<ScheduleChromosome> population, int tournamentSize) {
        ScheduleChromosome best = null;
        
        for (int i = 0; i < tournamentSize; i++) {
            ScheduleChromosome candidate = population.get(random.nextInt(population.size()));
            if (best == null || candidate.getFitness() > best.getFitness()) {
                best = candidate;
            }
        }
        
        return best;
    }

    /**
     * Order crossover (OX) - suitable for permutation representation.
     * Handles -1 (unassigned) values by treating them as unique values.
     */
    private ScheduleChromosome[] crossover(
            ScheduleChromosome parent1,
            ScheduleChromosome parent2,
            List<ShiftRequirementInfo> requirements,
            List<Staff> staffPool) {
        
        ScheduleChromosome child1 = new ScheduleChromosome(requirements, staffPool);
        ScheduleChromosome child2 = new ScheduleChromosome(requirements, staffPool);
        
        int size = parent1.getGenes().length;
        if (size < 2) {
            child1.getGenes()[0] = parent1.getGenes()[0];
            child2.getGenes()[0] = parent2.getGenes()[0];
            return new ScheduleChromosome[]{child1, child2};
        }
        
        // Select two crossover points
        int point1 = random.nextInt(size);
        int point2 = random.nextInt(size);
        if (point1 > point2) {
            int temp = point1;
            point1 = point2;
            point2 = temp;
        }
        
        // Copy segment from parent1 to child1
        System.arraycopy(parent1.getGenes(), point1, child1.getGenes(), point1, point2 - point1);
        
        // Fill remaining with parent2 values (in order), skip -1 values from parent2
        int pos = point2;
        for (int i = 0; i < size; i++) {
            int idx = (point2 + i) % size;
            int value = parent2.getGenes()[idx];
            
            // Skip -1 (unassigned) values - they shouldn't be inherited through crossover
            if (value == -1) continue;
            
            // Check if value already in child
            boolean exists = false;
            for (int j = 0; j < size; j++) {
                if (child1.getGenes()[j] == value) {
                    exists = true;
                    break;
                }
            }
            
            if (!exists) {
                child1.getGenes()[pos % size] = value;
                pos++;
            }
        }
        
        // Fill any remaining slots with valid random assignment
        for (int i = 0; i < size; i++) {
            if (child1.getGenes()[i] == -1) {
                // Assign random staff to fill the gap
                child1.getGenes()[i] = random.nextInt(staffPool.size());
            }
        }
        
        // Same for child2
        System.arraycopy(parent2.getGenes(), point1, child2.getGenes(), point1, point2 - point1);
        pos = point2;
        for (int i = 0; i < size; i++) {
            int idx = (point2 + i) % size;
            int value = parent1.getGenes()[idx];
            
            // Skip -1 values
            if (value == -1) continue;
            
            boolean exists = false;
            for (int j = 0; j < size; j++) {
                if (child2.getGenes()[j] == value) {
                    exists = true;
                    break;
                }
            }
            
            if (!exists) {
                child2.getGenes()[pos % size] = value;
                pos++;
            }
        }
        
        // Fill remaining slots
        for (int i = 0; i < size; i++) {
            if (child2.getGenes()[i] == -1) {
                child2.getGenes()[i] = random.nextInt(staffPool.size());
            }
        }
        
        return new ScheduleChromosome[]{child1, child2};
    }

    /**
     * Mutation: random swap of genes.
     * Avoids creating -1 (unassigned) values.
     */
    private void mutate(
            ScheduleChromosome chromosome,
            List<ShiftRequirementInfo> requirements,
            List<Staff> staffPool,
            List<LeaveRequest> leaveRequests,
            Set<String> existingCompensationDays,
            Set<Integer> excludedStaffIds,
            GeneticAlgorithmConfig config) {
        
        int size = chromosome.getGenes().length;
        if (size < 2) return;
        
        // Swap two random positions
        int pos1 = random.nextInt(size);
        int pos2 = random.nextInt(size);
        
        // Get current values
        int val1 = chromosome.getGenes()[pos1];
        int val2 = chromosome.getGenes()[pos2];
        
        // Ensure no -1 (unassigned) values are created
        // If a gene is -1, assign a random valid staff
        if (val1 < 0) val1 = random.nextInt(staffPool.size());
        if (val2 < 0) val2 = random.nextInt(staffPool.size());
        
        chromosome.setStaffAt(pos1, val1);
        chromosome.setStaffAt(pos2, val2);
    }

    /**
     * Build scheduling result from best chromosome.
     * Uses greedy assignment to fill ALL requirements with conflict-free staff.
     */
    private SchedulingResult buildResult(
            ScheduleChromosome bestSolution,
            List<ShiftRequirementInfo> requirements,
            List<Staff> activeStaff,
            long startTime) {
        
        Map<String, String> assignments = new HashMap<>();
        List<String> errors = new ArrayList<>();
        
        if (bestSolution == null) {
            return SchedulingResult.builder()
                    .valid(false)
                    .errors(List.of("Không tìm thấy giải pháp hợp lệ"))
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
        
        // Track: for each date, which shift types already have staff assigned
        // Key: "date_shiftTypeId", Value: Set of staffIds already assigned
        Map<String, Set<Integer>> dateShiftStaffMap = new HashMap<>();
        
        // First pass: record assignments from chromosome (valid ones)
        int[] genes = bestSolution.getGenes();
        for (int i = 0; i < requirements.size(); i++) {
            Staff staff = bestSolution.getStaffAt(i);
            if (staff == null) continue;
            
            ShiftRequirementInfo req = requirements.get(i);
            String shiftKey = req.workDate() + "_" + req.shiftTypeId();
            
            Set<Integer> staffSet = dateShiftStaffMap.computeIfAbsent(shiftKey, k -> new HashSet<>());
            staffSet.add(staff.getId());
        }
        
        // Second pass: greedy fill ALL unassigned requirements
        for (ShiftRequirementInfo req : requirements) {
            String shiftKey = req.workDate() + "_" + req.shiftTypeId();
            
            // Check if this shift already has staff
            Set<Integer> assigned = dateShiftStaffMap.getOrDefault(shiftKey, Collections.emptySet());
            if (!assigned.isEmpty()) continue; // Already filled
            
            // Find first available staff for this requirement
            for (Staff staff : activeStaff) {
                String staffDateKey = staff.getId() + "_" + req.workDate();
                
                // Check if staff already assigned to any shift on this date
                boolean staffBusy = false;
                for (Set<Integer> staffIds : dateShiftStaffMap.values()) {
                    if (staffIds.contains(staff.getId()) && staffDateKey.startsWith(staff.getId() + "_")) {
                        staffBusy = true;
                        break;
                    }
                }
                if (staffBusy) continue;
                
                // Assign this staff
                assigned.add(staff.getId());
                dateShiftStaffMap.put(shiftKey, assigned);
                assignments.put(staffDateKey, req.shiftTypeId());
                break;
            }
        }
        
        // Add conflicts to errors
        if (bestSolution.getConflictCount() > 0) {
            errors.add("Còn " + bestSolution.getConflictCount() + " xung đột chưa được giải quyết");
        }
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        // Count filled requirements
        long filledCount = requirements.stream()
                .filter(req -> dateShiftStaffMap.containsKey(req.workDate() + "_" + req.shiftTypeId()))
                .count();
        
        return SchedulingResult.builder()
                .assignments(assignments)
                .valid(bestSolution.getConflictCount() == 0)
                .errors(errors)
                .totalScore(bestSolution.getFitness() > 0 ? 
                        BigDecimal.valueOf(bestSolution.getFitness()) : BigDecimal.ZERO)
                .fairnessScore(BigDecimal.valueOf(bestSolution.getBalanceScore() * 100))
                .coverageScore(requirements.isEmpty() ? BigDecimal.valueOf(100) : 
                        BigDecimal.valueOf((double) filledCount / requirements.size() * 100))
                .scheduleCount(assignments.size())
                .executionTimeMs(executionTime)
                .build();
    }

    // Required by interface but not implemented for incremental solve
    @Override
    public SchedulingResult reSolve(
            SchedulingResult previousResult,
            ScheduleChange deltaChanges,
            List<Staff> staffList,
            List<ShiftRequirementInfo> requirements,
            List<LeaveRequest> leaveRequests) {
        // GA doesn't support incremental solving yet - do full solve
        return solve(staffList, null, null, requirements, null, leaveRequests, null);
    }

    @Override
    public boolean canReSolveIncrementally(ScheduleChange deltaChanges) {
        return false;
    }
}
