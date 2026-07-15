package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.Holiday;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Centralized holiday validation service.
 * Prevents duplicate holiday validation logic across multiple services.
 * Use this service instead of calling {@code holidayRepository.existsByHolidayDateAndIsActiveTrue()} directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HolidayValidationService {

    private final HolidayRepository holidayRepository;

    /**
     * Check if a date is a configured holiday.
     */
    public boolean isHoliday(LocalDate date) {
        if (date == null) return false;
        return holidayRepository.existsByHolidayDateAndIsActiveTrue(date);
    }

    /**
     * Throw BadRequestException if the date is a holiday.
     */
    public void validateNotHoliday(LocalDate date, String fieldName) {
        if (date == null) return;
        if (isHoliday(date)) {
            throw new BadRequestException(
                "Ngày " + date + " là ngày nghỉ lễ. Không thể xếp lịch vào ngày nghỉ lễ.");
        }
    }

    /**
     * Get all active holidays in a date range as a Set.
     */
    public Set<LocalDate> getActiveHolidaysBetween(LocalDate start, LocalDate end) {
        return holidayRepository.findActiveHolidaysBetween(start, end)
                .stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());
    }
}
