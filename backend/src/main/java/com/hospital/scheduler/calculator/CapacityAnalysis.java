package com.hospital.scheduler.calculator;

import com.hospital.scheduler.scheduling.config.ConfigDomain;

import java.util.List;
import java.util.Map;

/**
 * Unified analysis result from any algorithm analyzer.
 */
public class CapacityAnalysis {

    private boolean feasible;
    private int totalRequirement;
    private int totalCapacity;
    private int totalAssigned;
    private int totalStaff;
    private int periodDays;

    private List<ShiftTypeCapacity> perShiftType;
    private List<Bottleneck> bottlenecks;
    private HolidayImpact holidayImpact;
    private AlgorithmRun algorithmRun;

    // Mode 2,3: recommended config
    private ConfigDomain recommendedConfig;
    private List<ConfigChange> configChanges;
    private String recommendedAlgorithm;

    private Double expectedCoverage;
    private Double expectedFairness;

    public CapacityAnalysis() {}

    // ── Nested types (mirror DTOs for internal use) ──

    public record ShiftTypeCapacity(
            String shiftType,
            int requirement,
            int maxPossible,
            int assigned,
            int eligibleStaffCount,
            double avgDomainSize,
            int minDomainSize,
            int bottleneckCount,
            Map<String, Integer> perSpecialty
    ) {}

    public record Bottleneck(
            String type,
            String shiftType,
            String specialty,
            String severity,
            String message,
            String suggestion
    ) {}

    public record HolidayImpact(
            int holidayDaysCount,
            Map<String, Integer> skippedShifts,
            String mode
    ) {}

    public record AlgorithmRun(
            String type,
            long executionTimeMs,
            String terminatedBy,
            int varsExplored,
            int assignmentsMade
    ) {}

    public record ConfigChange(
            String field,
            Object fromValue,
            Object toValue,
            String reason
    ) {}

    // ── Getters/Setters ──

    public boolean isFeasible() { return feasible; }
    public void setFeasible(boolean feasible) { this.feasible = feasible; }

    public int getTotalRequirement() { return totalRequirement; }
    public void setTotalRequirement(int totalRequirement) { this.totalRequirement = totalRequirement; }

    public int getTotalCapacity() { return totalCapacity; }
    public void setTotalCapacity(int totalCapacity) { this.totalCapacity = totalCapacity; }

    public int getTotalAssigned() { return totalAssigned; }
    public void setTotalAssigned(int totalAssigned) { this.totalAssigned = totalAssigned; }

    public int getTotalStaff() { return totalStaff; }
    public void setTotalStaff(int totalStaff) { this.totalStaff = totalStaff; }

    public int getPeriodDays() { return periodDays; }
    public void setPeriodDays(int periodDays) { this.periodDays = periodDays; }

    public List<ShiftTypeCapacity> getPerShiftType() { return perShiftType; }
    public void setPerShiftType(List<ShiftTypeCapacity> perShiftType) { this.perShiftType = perShiftType; }

    public List<Bottleneck> getBottlenecks() { return bottlenecks; }
    public void setBottlenecks(List<Bottleneck> bottlenecks) { this.bottlenecks = bottlenecks; }

    public HolidayImpact getHolidayImpact() { return holidayImpact; }
    public void setHolidayImpact(HolidayImpact holidayImpact) { this.holidayImpact = holidayImpact; }

    public AlgorithmRun getAlgorithmRun() { return algorithmRun; }
    public void setAlgorithmRun(AlgorithmRun algorithmRun) { this.algorithmRun = algorithmRun; }

    public ConfigDomain getRecommendedConfig() { return recommendedConfig; }
    public void setRecommendedConfig(ConfigDomain recommendedConfig) { this.recommendedConfig = recommendedConfig; }

    public List<ConfigChange> getConfigChanges() { return configChanges; }
    public void setConfigChanges(List<ConfigChange> configChanges) { this.configChanges = configChanges; }

    public String getRecommendedAlgorithm() { return recommendedAlgorithm; }
    public void setRecommendedAlgorithm(String recommendedAlgorithm) { this.recommendedAlgorithm = recommendedAlgorithm; }

    public Double getExpectedCoverage() { return expectedCoverage; }
    public void setExpectedCoverage(Double expectedCoverage) { this.expectedCoverage = expectedCoverage; }

    public Double getExpectedFairness() { return expectedFairness; }
    public void setExpectedFairness(Double expectedFairness) { this.expectedFairness = expectedFairness; }
}
