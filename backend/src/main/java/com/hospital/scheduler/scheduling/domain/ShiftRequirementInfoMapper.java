package com.hospital.scheduler.scheduling.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mapper for ShiftRequirementInfo between algorithm and scheduling packages.
 */
public class ShiftRequirementInfoMapper {

    /**
     * Convert algorithm ShiftRequirementInfo to scheduling domain ShiftRequirementInfo.
     */
    public static com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo toDomain(
            com.hospital.scheduler.algorithm.ShiftRequirementInfo source) {
        
        if (source == null) return null;
        
        return com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo.builder()
                .slotId(source.getSlotId())
                .date(source.getDate())
                .shiftTypeId(source.getShiftTypeId())
                .specialtyId(source.getSpecialtyId())
                .requiredStaffCount(source.getRequiredStaffCount())
                .hours(source.getHours())
                .startTime(source.getStartTime())
                .endTime(source.getEndTime())
                .weekNumber(source.getWeekNumber())
                .build();
    }

    /**
     * Convert list of algorithm ShiftRequirementInfo to scheduling domain.
     */
    public static java.util.List<com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo> toDomainList(
            java.util.List<com.hospital.scheduler.algorithm.ShiftRequirementInfo> source) {
        
        if (source == null) return java.util.Collections.emptyList();
        
        return source.stream()
                .map(ShiftRequirementInfoMapper::toDomain)
                .collect(java.util.stream.Collectors.toList());
    }
}
