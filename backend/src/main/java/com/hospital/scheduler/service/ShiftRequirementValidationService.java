package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.repository.ShiftRequirementRepository;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import com.hospital.scheduler.repository.SpecialtyRepository;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.entity.Holiday;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.Specialty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates shift requirements coverage for a period.
 * Used by AutoSchedulingService to ensure all days have requirements before scheduling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftRequirementValidationService {

    private final ShiftRequirementRepository requirementRepository;
    private final HolidayRepository holidayRepository;
    private final SpecialtyRepository specialtyRepository;

    public record CoverageResult(boolean isComplete, int totalMissing, Map<LocalDate, List<String>> missingByDate) {}

    /**
     * Check that requirements cover all days in the period based on AutoGenConfig.
     */
    @Transactional(readOnly = true)
    public CoverageResult validateCoverage(SchedulePeriod period, AutoGenConfig config) {
        List<ShiftRequirement> existing = requirementRepository.findByPeriodId(period.getId());
        Set<LocalDate> holidays = holidayRepository.findActiveHolidaysBetween(period.getStartDate(), period.getEndDate())
                .stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        Set<String> removedTypes = config.removedShiftTypes() == null
                ? Set.of()
                : config.removedShiftTypes().stream().map(String::toUpperCase).collect(Collectors.toSet());

        // Build set of (date, shiftTypeId) pairs that should exist
        Set<String> requiredPairs = new HashSet<>();
        LocalDate current = period.getStartDate();
        while (!current.isAfter(period.getEndDate())) {
            boolean isHoliday = holidays.contains(current);
            boolean isPartialHoliday = "PARTIAL".equalsIgnoreCase(config.holidayMode());

            // L01: skip if full holiday and not PARTIAL mode
            if ((!isHoliday || isPartialHoliday) && !removedTypes.contains("L01")) {
                requiredPairs.add(current + "|L01");
            }
            // L02: same as L01
            if ((!isHoliday || isPartialHoliday) && !removedTypes.contains("L02")) {
                requiredPairs.add(current + "|L02");
            }
            // L03: skip on holidays unless PARTIAL mode
            if ((!isHoliday || isPartialHoliday) && !removedTypes.contains("L03")) {
                requiredPairs.add(current + "|L03");
            }
            // L04: requires specialty
            if ((!isHoliday || isPartialHoliday) && !removedTypes.contains("L04")) {
                List<Specialty> specialties = specialtyRepository.findByIsActiveTrue();
                if (!specialties.isEmpty()) {
                    requiredPairs.add(current + "|L04");
                }
            }
            current = current.plusDays(1);
        }

        // Build set of existing (date, shiftTypeId) pairs
        Set<String> existingPairs = new HashSet<>();
        if (existing != null) {
            for (ShiftRequirement r : existing) {
                existingPairs.add(r.getWorkDate() + "|" + r.getShiftType().getId());
            }
        }

        // Find missing pairs
        Map<LocalDate, List<String>> missingByDate = new LinkedHashMap<>();
        for (String pair : requiredPairs) {
            if (!existingPairs.contains(pair)) {
                String[] parts = pair.split("\\|");
                LocalDate date = LocalDate.parse(parts[0]);
                String shiftType = parts[1];
                missingByDate.computeIfAbsent(date, k -> new ArrayList<>()).add(shiftType);
            }
        }

        int totalMissing = missingByDate.values().stream().mapToInt(List::size).sum();
        boolean complete = missingByDate.isEmpty();

        if (!complete) {
            log.warn("Requirements coverage INCOMPLETE for period {}: {} days missing, {} shift types missing",
                    period.getId(), missingByDate.size(), totalMissing);
        }

        return new CoverageResult(complete, totalMissing, missingByDate);
    }

    /**
     * Throw BadRequestException if requirements coverage is incomplete.
     * Used by apply mode.
     */
    public void requireCompleteCoverage(SchedulePeriod period, AutoGenConfig config) {
        CoverageResult result = validateCoverage(period, config);
        if (!result.isComplete()) {
            int daysMissing = result.missingByDate().size();
            String dateList = result.missingByDate().keySet().stream()
                    .limit(5)
                    .map(LocalDate::toString)
                    .collect(java.util.stream.Collectors.joining(", "));
            String summary = "Thiếu requirements cho " + daysMissing + " ngày trong kỳ lịch. "
                    + "Các ngày thiếu: " + dateList + (daysMissing > 5 ? "..." : "")
                    + ". Vui lòng gọi POST /api/v1/shift-requirements/regenerate/" + period.getId() + " trước khi xếp lịch.";
            throw new BadRequestException(summary);
        }
    }
}