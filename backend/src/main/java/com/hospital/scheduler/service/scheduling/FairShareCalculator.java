package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes demand-based fair share per shift type.
 * Used as per-type cap in Greedy and Fair Greedy comparators.
 *
 * <p>Spec M07-F05: L04 is specialty-bound (M05) — pool for L04 is staff with
 * matching specialty, not the full staff pool. L01/L02/L03 use the full staff pool.
 */
@Slf4j
@Component
public class FairShareCalculator {

    public Map<String, Integer> computeFairSharePerType(List<ShiftRequirement> requirements, int staffPool) {
        return computeFairSharePerTypeWithStaff(requirements, staffPool, null);
    }

    public Map<String, Integer> computeFairSharePerTypeWithStaff(
            List<ShiftRequirement> requirements, int staffPool, List<Staff> activeStaff) {
        if (requirements == null || requirements.isEmpty()) {
            return Map.of(
                "L01", 1,
                "L02", 1,
                "L03", 1,
                "L04", 1
            );
        }

        Map<String, Integer> result = new HashMap<>();
        List<Staff> safeActiveStaff = (activeStaff == null || activeStaff.isEmpty()) ? List.of() : activeStaff;

        for (String typeId : List.of("L01", "L02", "L03", "L04")) {
            int totalDemand = requirements.stream()
                    .filter(r -> typeId.equals(r.getShiftType().getId()))
                    .mapToInt(ShiftRequirement::getRequiredStaffCount)
                    .sum();

            int effectivePool;
            if ("L04".equals(typeId) && !safeActiveStaff.isEmpty()) {
                // L04 luôn strict-specialty (không cross): pool = staff đúng chuyên khoa.
                Set<Integer> l04SpecialtyIds = requirements.stream()
                        .filter(r -> typeId.equals(r.getShiftType().getId()) && r.getSpecialty() != null)
                        .map(r -> r.getSpecialty().getId())
                        .collect(Collectors.toSet());

                if (!l04SpecialtyIds.isEmpty()) {
                    long eligibleL04Count = safeActiveStaff.stream()
                            .filter(s -> s.getSpecialty() != null && l04SpecialtyIds.contains(s.getSpecialty().getId()))
                            .count();
                    effectivePool = Math.max(1, (int) eligibleL04Count);

                    // Per-specialty fair share
                    for (Integer specId : l04SpecialtyIds) {
                        int specDemand = requirements.stream()
                                .filter(r -> typeId.equals(r.getShiftType().getId())
                                        && r.getSpecialty() != null
                                        && specId.equals(r.getSpecialty().getId()))
                                .mapToInt(ShiftRequirement::getRequiredStaffCount)
                                .sum();

                        long specPool = safeActiveStaff.stream()
                                .filter(s -> s.getSpecialty() != null && specId.equals(s.getSpecialty().getId()))
                                .count();
                        int specEffectivePool = Math.max(1, (int) specPool);
                        int specFairShare = specDemand > 0
                                ? (int) Math.ceil((double) specDemand / specEffectivePool) : 1;
                        result.put("L04:" + specId, specFairShare);
                    }
                } else {
                    effectivePool = staffPool;
                }
            } else {
                effectivePool = staffPool;
            }

            int fairShare = totalDemand > 0 ? (int) Math.ceil((double) totalDemand / effectivePool) : 1;
            result.put(typeId, fairShare);
        }

        log.info("fairSharePerType: L01={} L02={} L03={} L04={} (staffPool={})",
                result.get("L01"), result.get("L02"), result.get("L03"), result.get("L04"), staffPool);

        result.entrySet().stream()
                .filter(e -> e.getKey().startsWith("L04:"))
                .forEach(e -> log.info("  fairShare {}: {}", e.getKey(), e.getValue()));

        return result;
    }
}
