package com.hospital.scheduler.dto;

import com.hospital.scheduler.scheduling.config.ConfigDomain;

import java.util.Map;
import java.util.Set;

/**
 * Unified request for Configuration Calculator (all 3 modes).
 *
 * <pre>
 * Mode 1 (Config + Algorithm → Capacity):
 *   periodId, algorithmType, configOverride (Map, partial update)
 *
 * Mode 2 (Target + Algorithm → Config):
 *   periodId, algorithmType, targetShifts, enabledGroups (optional)
 *
 * Mode 3 (Target → Config + Algorithm):
 *   periodId, targetShifts, enabledGroups (optional)
 * </pre>
 *
 * <p>{@code enabledGroups} lets the user restrict which config groups the
 * backend is allowed to tune when searching for a feasible config:
 * <ul>
 *   <li>{@code staffing} — Giới hạn xếp lịch (maxShiftsPerStaff, maxStaffPerShift, minStaffPerShift)</li>
 *   <li>{@code perShift} — Giới hạn theo loại ca (L01-L04: minPerDay/maxPerDay/maxPerWeek)</li>
 *   <li>{@code holiday} — Ngày lễ (holidayMode, removedShiftTypes)</li>
 *   <li>{@code l04} — PK Chuyên gia (l04CrossSpecialty, ratio, strategy, specialties)</li>
 * </ul>
 * If null/empty, all groups are tunable (default behaviour).
 */
public class ConfigCalculatorRequest {

    /** 1 = Configuration→Capacity, 2 = Target→Config, 3 = Target→Config+Algorithm */
    private int mode;

    private Integer periodId;

    /** Algorithm type (GREEDY, FAIR_GREEDY, CSP_MRV_FC, V10_LOCAL_SEARCH). Null for mode 3. */
    private String algorithmType;

    /** Target shift counts per type (L01-L04). Used in mode 2 and 3. */
    private Map<String, Integer> targetShifts;

    /**
     * Optional config overrides as a flat map. Only supplied keys override the
     * current DB config. Using Map instead of ConfigDomain to allow partial
     * updates without Jackson filling defaults for missing fields.
     */
    private Map<String, Object> configOverride;

    /**
     * Optional whitelist of config groups the backend may tune in mode 2/3.
     * Valid values: "staffing", "perShift", "holiday", "l04".
     * Null or empty = all groups are tunable.
     */
    private Set<String> enabledGroups;

    public ConfigCalculatorRequest() {}

    public ConfigCalculatorRequest(int mode, Integer periodId, String algorithmType,
                                   Map<String, Integer> targetShifts, Map<String, Object> configOverride) {
        this.mode = mode;
        this.periodId = periodId;
        this.algorithmType = algorithmType;
        this.targetShifts = targetShifts;
        this.configOverride = configOverride;
    }

    public int getMode() { return mode; }
    public void setMode(int mode) { this.mode = mode; }

    public Integer getPeriodId() { return periodId; }
    public void setPeriodId(Integer periodId) { this.periodId = periodId; }

    public String getAlgorithmType() { return algorithmType; }
    public void setAlgorithmType(String algorithmType) { this.algorithmType = algorithmType; }

    public Map<String, Integer> getTargetShifts() { return targetShifts; }
    public void setTargetShifts(Map<String, Integer> targetShifts) { this.targetShifts = targetShifts; }

    public Map<String, Object> getConfigOverride() { return configOverride; }
    public void setConfigOverride(Map<String, Object> configOverride) { this.configOverride = configOverride; }

    public Set<String> getEnabledGroups() { return enabledGroups; }
    public void setEnabledGroups(Set<String> enabledGroups) { this.enabledGroups = enabledGroups; }
}
