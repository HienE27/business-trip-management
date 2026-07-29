package com.hospital.scheduler.dto;

import com.hospital.scheduler.scheduling.config.ConfigDomain;

import java.util.List;
import java.util.Map;

/**
 * Unified response for Configuration Calculator (all 3 modes).
 */
public class ConfigCalculatorResponse {

    private int mode;
    private boolean feasible;
    private String message;

    // ── Period info ──
    private Integer periodId;
    private String periodName;
    private int periodDays;
    private int totalStaff;

    // ── Capacity breakdown per shift type ──
    private List<ShiftTypeCapacity> perShiftType;

    // ── Bottlenecks ──
    private List<Bottleneck> bottlenecks;

    // ── Holiday impact ──
    private HolidayImpact holidayImpact;

    // ── Algorithm info ──
    private AlgorithmInfo algorithmInfo;

    // ── Mode 2,3: recommended config ──
    private ConfigDomain recommendedConfig;
    private List<ConfigChange> configChanges;
    private String recommendedAlgorithm; // mode 3 only

    // ── Mode 2 aggregate path: auto-derived L01-L04 targets ──
    private Map<String, Integer> derivedTargetShifts;

    // ── Expected quality ──
    private Double expectedCoverage;
    private Double expectedFairness;

    // ── Total shift counts ──
    private int totalRequirement;   // total shifts needed (from config)
    private int totalCapacity;      // max shifts achievable
    private int totalAssigned;      // shifts actually assigned in analysis run

    public ConfigCalculatorResponse() {}

    // ── Nested records ──

    public static class ShiftTypeCapacity {
        private String shiftType;       // L01-L04
        private int requirement;        // shifts needed (from config)
        private int maxPossible;        // theoretical upper bound
        private int assigned;           // actually assigned in analysis run
        private int eligibleStaffCount;
        private double avgDomainSize;
        private int minDomainSize;
        private int bottleneckCount;    // vars with domain ≤ 2
        private Map<String, Integer> perSpecialty; // L04 only

        public ShiftTypeCapacity() {}

        public String getShiftType() { return shiftType; }
        public void setShiftType(String shiftType) { this.shiftType = shiftType; }
        public int getRequirement() { return requirement; }
        public void setRequirement(int requirement) { this.requirement = requirement; }
        public int getMaxPossible() { return maxPossible; }
        public void setMaxPossible(int maxPossible) { this.maxPossible = maxPossible; }
        public int getAssigned() { return assigned; }
        public void setAssigned(int assigned) { this.assigned = assigned; }
        public int getEligibleStaffCount() { return eligibleStaffCount; }
        public void setEligibleStaffCount(int eligibleStaffCount) { this.eligibleStaffCount = eligibleStaffCount; }
        public double getAvgDomainSize() { return avgDomainSize; }
        public void setAvgDomainSize(double avgDomainSize) { this.avgDomainSize = avgDomainSize; }
        public int getMinDomainSize() { return minDomainSize; }
        public void setMinDomainSize(int minDomainSize) { this.minDomainSize = minDomainSize; }
        public int getBottleneckCount() { return bottleneckCount; }
        public void setBottleneckCount(int bottleneckCount) { this.bottleneckCount = bottleneckCount; }
        public Map<String, Integer> getPerSpecialty() { return perSpecialty; }
        public void setPerSpecialty(Map<String, Integer> perSpecialty) { this.perSpecialty = perSpecialty; }
    }

    public static class Bottleneck {
        private String type;           // STAFF_SHORTAGE, SPECIALTY_MISMATCH, MAX_SHIFT_LIMIT, CONSTRAINT_BLOCK, HOLIDAY_BLOCK
        private String shiftType;
        private String specialty;
        private String severity;       // HIGH, MEDIUM, LOW
        private String message;
        private String suggestion;

        public Bottleneck() {}

        public Bottleneck(String type, String shiftType, String severity, String message, String suggestion) {
            this.type = type;
            this.shiftType = shiftType;
            this.severity = severity;
            this.message = message;
            this.suggestion = suggestion;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getShiftType() { return shiftType; }
        public void setShiftType(String shiftType) { this.shiftType = shiftType; }
        public String getSpecialty() { return specialty; }
        public void setSpecialty(String specialty) { this.specialty = specialty; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getSuggestion() { return suggestion; }
        public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    }

    public static class HolidayImpact {
        private int holidayDaysCount;
        private Map<String, Integer> skippedShifts; // per shift type
        private String mode; // SKIP or PARTIAL

        public HolidayImpact() {}

        public int getHolidayDaysCount() { return holidayDaysCount; }
        public void setHolidayDaysCount(int holidayDaysCount) { this.holidayDaysCount = holidayDaysCount; }
        public Map<String, Integer> getSkippedShifts() { return skippedShifts; }
        public void setSkippedShifts(Map<String, Integer> skippedShifts) { this.skippedShifts = skippedShifts; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
    }

    public static class AlgorithmInfo {
        private String type;
        private long executionTimeMs;
        private String terminatedBy;   // COMPLETE, TIMEOUT, DEAD_END, BOUND_REACHED
        private int varsExplored;
        private int assignmentsMade;

        public AlgorithmInfo() {}

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
        public String getTerminatedBy() { return terminatedBy; }
        public void setTerminatedBy(String terminatedBy) { this.terminatedBy = terminatedBy; }
        public int getVarsExplored() { return varsExplored; }
        public void setVarsExplored(int varsExplored) { this.varsExplored = varsExplored; }
        public int getAssignmentsMade() { return assignmentsMade; }
        public void setAssignmentsMade(int assignmentsMade) { this.assignmentsMade = assignmentsMade; }
    }

    public static class ConfigChange {
        private String field;
        private Object fromValue;
        private Object toValue;
        private String reason;

        public ConfigChange() {}

        public ConfigChange(String field, Object fromValue, Object toValue, String reason) {
            this.field = field;
            this.fromValue = fromValue;
            this.toValue = toValue;
            this.reason = reason;
        }

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public Object getFromValue() { return fromValue; }
        public void setFromValue(Object fromValue) { this.fromValue = fromValue; }
        public Object getToValue() { return toValue; }
        public void setToValue(Object toValue) { this.toValue = toValue; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    // ── Getters/setters for top-level fields ──

    public int getMode() { return mode; }
    public void setMode(int mode) { this.mode = mode; }
    public boolean isFeasible() { return feasible; }
    public void setFeasible(boolean feasible) { this.feasible = feasible; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Integer getPeriodId() { return periodId; }
    public void setPeriodId(Integer periodId) { this.periodId = periodId; }
    public String getPeriodName() { return periodName; }
    public void setPeriodName(String periodName) { this.periodName = periodName; }
    public int getPeriodDays() { return periodDays; }
    public void setPeriodDays(int periodDays) { this.periodDays = periodDays; }
    public int getTotalStaff() { return totalStaff; }
    public void setTotalStaff(int totalStaff) { this.totalStaff = totalStaff; }

    public List<ShiftTypeCapacity> getPerShiftType() { return perShiftType; }
    public void setPerShiftType(List<ShiftTypeCapacity> perShiftType) { this.perShiftType = perShiftType; }
    public List<Bottleneck> getBottlenecks() { return bottlenecks; }
    public void setBottlenecks(List<Bottleneck> bottlenecks) { this.bottlenecks = bottlenecks; }

    public HolidayImpact getHolidayImpact() { return holidayImpact; }
    public void setHolidayImpact(HolidayImpact holidayImpact) { this.holidayImpact = holidayImpact; }

    public AlgorithmInfo getAlgorithmInfo() { return algorithmInfo; }
    public void setAlgorithmInfo(AlgorithmInfo algorithmInfo) { this.algorithmInfo = algorithmInfo; }

    public ConfigDomain getRecommendedConfig() { return recommendedConfig; }
    public void setRecommendedConfig(ConfigDomain recommendedConfig) { this.recommendedConfig = recommendedConfig; }
    public List<ConfigChange> getConfigChanges() { return configChanges; }
    public void setConfigChanges(List<ConfigChange> configChanges) { this.configChanges = configChanges; }
    public String getRecommendedAlgorithm() { return recommendedAlgorithm; }
    public void setRecommendedAlgorithm(String recommendedAlgorithm) { this.recommendedAlgorithm = recommendedAlgorithm; }

    public Map<String, Integer> getDerivedTargetShifts() { return derivedTargetShifts; }
    public void setDerivedTargetShifts(Map<String, Integer> derivedTargetShifts) { this.derivedTargetShifts = derivedTargetShifts; }

    public Double getExpectedCoverage() { return expectedCoverage; }
    public void setExpectedCoverage(Double expectedCoverage) { this.expectedCoverage = expectedCoverage; }
    public Double getExpectedFairness() { return expectedFairness; }
    public void setExpectedFairness(Double expectedFairness) { this.expectedFairness = expectedFairness; }

    public int getTotalRequirement() { return totalRequirement; }
    public void setTotalRequirement(int totalRequirement) { this.totalRequirement = totalRequirement; }
    public int getTotalCapacity() { return totalCapacity; }
    public void setTotalCapacity(int totalCapacity) { this.totalCapacity = totalCapacity; }
    public int getTotalAssigned() { return totalAssigned; }
    public void setTotalAssigned(int totalAssigned) { this.totalAssigned = totalAssigned; }
}
