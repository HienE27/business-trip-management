package com.hospital.scheduler.scheduling.domain;

import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable problem definition for the scheduling algorithm.
 * 
 * <p>Contains all input data needed to solve the scheduling problem:
 * staff, requirements, leaves, holidays, compensation days, and configuration.</p>
 * 
 * <p>This is a pure data object - no mutations allowed after construction.</p>
 */
@Getter
@Builder
public final class SchedulingProblem {

    private final List<StaffNode> staffList;
    private final Map<Integer, StaffNode> staffById;
    private final List<ShiftRequirementInfo> requirements;
    private final Map<Integer, ShiftRequirementInfo> requirementsById;
    private final Map<Integer, Set<LocalDate>> leavesByStaff;
    private final Set<LocalDate> holidays;
    private final Map<Integer, Set<LocalDate>> compensationDaysByStaff;
    private final SchedulingConfig config;

    /**
     * Build SchedulingProblem from raw inputs.
     */
    public static SchedulingProblem from(
            List<Staff> staffList,
            List<ShiftRequirementInfo> requirements,
            List<LeaveRequest> leaveRequests,
            Set<String> existingCompensationDays,
            SchedulingConfig config) {

        // Build staff nodes
        List<StaffNode> staffNodes = staffList.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .map(StaffNode::from)
                .collect(Collectors.toList());

        Map<Integer, StaffNode> staffById = staffNodes.stream()
                .collect(Collectors.toMap(StaffNode::getId, s -> s));

        // Build requirements map
        Map<Integer, ShiftRequirementInfo> requirementsById = requirements.stream()
                .collect(Collectors.toMap(ShiftRequirementInfo::getSlotId, r -> r));

        // Build leaves map (approved leaves only)
        Map<Integer, Set<LocalDate>> leavesByStaff = buildLeavesMap(leaveRequests);

        // Build compensation days map
        Map<Integer, Set<LocalDate>> compensationDaysByStaff = buildCompensationDaysMap(
                existingCompensationDays, staffById);

        return SchedulingProblem.builder()
                .staffList(staffNodes)
                .staffById(staffById)
                .requirements(requirements)
                .requirementsById(requirementsById)
                .leavesByStaff(leavesByStaff)
                .holidays(Collections.emptySet()) // Holidays loaded separately
                .compensationDaysByStaff(compensationDaysByStaff)
                .config(config)
                .build();
    }

    /**
     * Build leaves map from leave requests.
     */
    private static Map<Integer, Set<LocalDate>> buildLeavesMap(List<LeaveRequest> leaveRequests) {
        Map<Integer, Set<LocalDate>> leavesByStaff = new HashMap<>();

        if (leaveRequests == null) {
            return leavesByStaff;
        }

        for (LeaveRequest leave : leaveRequests) {
            if (leave.getStatus() != LeaveRequest.LeaveStatus.APPROVED) {
                continue;
            }

            LocalDate from = leave.getStartDate();
            LocalDate to = leave.getEndDate() != null ? leave.getEndDate() : from;

            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                leavesByStaff
                        .computeIfAbsent(leave.getStaff().getId(), k -> new HashSet<>())
                        .add(d);
            }
        }

        return leavesByStaff;
    }

    /**
     * Build compensation days map from string set.
     */
    private static Map<Integer, Set<LocalDate>> buildCompensationDaysMap(
            Set<String> compDays,
            Map<Integer, StaffNode> staffById) {

        Map<Integer, Set<LocalDate>> result = new HashMap<>();

        if (compDays == null) {
            return result;
        }

        for (String compDay : compDays) {
            // Format: "staffId|yyyy-MM-dd" or "staffId_yyyy-MM-dd"
            String[] parts = compDay.split("[|_]");
            if (parts.length >= 2) {
                try {
                    int staffId = Integer.parseInt(parts[0]);
                    LocalDate date = LocalDate.parse(parts[1]);
                    result.computeIfAbsent(staffId, k -> new HashSet<>()).add(date);
                } catch (NumberFormatException | java.time.format.DateTimeParseException e) {
                    // Skip malformed entries
                }
            }
        }

        return result;
    }

    /**
     * Get staff by ID.
     */
    public StaffNode getStaff(int staffId) {
        return staffById.get(staffId);
    }

    /**
     * Get requirement by slot ID.
     */
    public ShiftRequirementInfo getRequirement(int slotId) {
        return requirementsById.get(slotId);
    }

    /**
     * Check if staff has leave on given date.
     */
    public boolean hasLeave(int staffId, LocalDate date) {
        Set<LocalDate> leaves = leavesByStaff.get(staffId);
        return leaves != null && leaves.contains(date);
    }

    /**
     * Check if date is a holiday.
     */
    public boolean isHoliday(LocalDate date) {
        return holidays != null && holidays.contains(date);
    }

    /**
     * Check if date is a compensation day for staff.
     */
    public boolean isCompensationDay(int staffId, LocalDate date) {
        Set<LocalDate> compDays = compensationDaysByStaff.get(staffId);
        return compDays != null && compDays.contains(date);
    }

    /**
     * Get list of eligible staff IDs for a given slot.
     */
    public List<Integer> getEligibleStaff(int slotId) {
        ShiftRequirementInfo req = requirementsById.get(slotId);
        if (req == null) {
            return Collections.emptyList();
        }

        return staffById.values().stream()
                .filter(staff -> staff.isEligibleFor(req.getShiftTypeId(), req.getSpecialtyId()))
                .filter(staff -> !hasLeave(staff.getId(), req.getDate()))
                .filter(staff -> !isCompensationDay(staff.getId(), req.getDate()))
                .map(StaffNode::getId)
                .collect(Collectors.toList());
    }

    /**
     * Get all requirements for a given date.
     */
    public List<ShiftRequirementInfo> getRequirementsOnDate(LocalDate date) {
        return requirements.stream()
                .filter(r -> r.getDate().equals(date))
                .collect(Collectors.toList());
    }

    /**
     * Get total number of staff.
     */
    public int getStaffCount() {
        return staffList.size();
    }

    /**
     * Get total number of requirements.
     */
    public int getRequirementCount() {
        return requirements.size();
    }

    /**
     * Get total required staff count across all requirements.
     */
    public int getTotalRequiredStaff() {
        return requirements.stream()
                .mapToInt(ShiftRequirementInfo::getRequiredStaffCount)
                .sum();
    }
}
