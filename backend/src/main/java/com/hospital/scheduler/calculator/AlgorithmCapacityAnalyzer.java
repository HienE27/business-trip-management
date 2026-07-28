package com.hospital.scheduler.calculator;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.scheduling.config.ConfigDomain;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Analyzes capacity for a specific algorithm type.
 * Each implementation runs the algorithm's actual logic (eligibility, constraints,
 * assignment rules) to determine max achievable shifts and identify bottlenecks.
 */
public interface AlgorithmCapacityAnalyzer {

    /**
     * Analyze capacity with the given configuration and data.
     *
     * @param period        the scheduling period
     * @param activeStaff   active staff list (already filtered for exclusions)
     * @param leaveRequests approved leave requests
     * @param holidays      active holidays in the period
     * @param specialties   active specialties
     * @param shiftTypes    shift type entities (L01-L04)
     * @param config        the config domain to use for analysis
     * @param autoGenConfig the auto-gen config derived from ConfigDomain
     * @return analysis result
     */
    CapacityAnalysis analyze(
            SchedulePeriod period,
            List<Staff> activeStaff,
            List<LeaveRequest> leaveRequests,
            List<Holiday> holidays,
            List<Specialty> specialties,
            List<ShiftType> shiftTypes,
            ConfigDomain config,
            AutoGenConfig autoGenConfig
    );

    /** The algorithm type this analyzer handles. */
    String supportedAlgorithmType();
}
