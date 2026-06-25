package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.HolidayRequest;
import com.hospital.scheduler.dto.request.NotificationDTO;
import com.hospital.scheduler.dto.response.HolidayResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.Holiday;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.security.AuthContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final AuditHistoryService auditHistoryService;
    private final NotificationService notificationService;
    private final AuthContextService authContextService;

    @Transactional(readOnly = true)
    public List<HolidayResponse> getAllHolidays() {
        return holidayRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> getActiveHolidays() {
        return holidayRepository.findByIsActiveTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> getHolidaysByYear(Integer year) {
        return holidayRepository.findByYear(year).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HolidayResponse getHolidayById(Integer id) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngày lễ với ID: " + id));
        return toResponse(holiday);
    }

    public HolidayResponse createHoliday(HolidayRequest request) {
        if (holidayRepository.existsByHolidayDate(request.getHolidayDate())) {
            throw new BadRequestException("Ngày lễ đã tồn tại: " + request.getHolidayDate());
        }

        Holiday holiday = Holiday.builder()
                .name(request.getName())
                .holidayDate(request.getHolidayDate())
                .year(request.getHolidayDate().getYear())
                .isNationalHoliday(request.getIsNationalHoliday() != null ? request.getIsNationalHoliday() : false)
                .description(request.getDescription())
                .isActive(true)
                .build();

        Holiday saved = holidayRepository.save(holiday);

        auditHistoryService.logAction("holiday", saved.getId(), AuditHistory.ActionType.INSERT,
                null, saved, authContextService.getCurrentStaff().getId());

        notificationService.createNotificationForAllStaff("Ngày nghỉ lễ mới",
                "Ngày " + holiday.getHolidayDate() + " đã được thêm vào danh sách ngày nghỉ lễ: " + holiday.getName());

        return toResponse(saved);
    }

    public HolidayResponse updateHoliday(Integer id, HolidayRequest request) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngày lễ với ID: " + id));

        if (!holiday.getHolidayDate().equals(request.getHolidayDate())
                && holidayRepository.existsByHolidayDate(request.getHolidayDate())) {
            throw new BadRequestException("Ngày lễ đã tồn tại: " + request.getHolidayDate());
        }

        holiday.setName(request.getName());
        holiday.setHolidayDate(request.getHolidayDate());
        holiday.setYear(request.getHolidayDate().getYear());
        holiday.setIsNationalHoliday(request.getIsNationalHoliday() != null ? request.getIsNationalHoliday() : false);
        holiday.setDescription(request.getDescription());

        Holiday oldHoliday = Holiday.builder()
                .name(holiday.getName())
                .holidayDate(holiday.getHolidayDate())
                .year(holiday.getYear())
                .isNationalHoliday(holiday.getIsNationalHoliday())
                .description(holiday.getDescription())
                .build();

        Holiday saved = holidayRepository.save(holiday);

        auditHistoryService.logAction("holiday", id, AuditHistory.ActionType.UPDATE,
                oldHoliday, saved, authContextService.getCurrentStaff().getId());

        return toResponse(saved);
    }

    public void deleteHoliday(Integer id) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngày lễ với ID: " + id));

        auditHistoryService.logAction("holiday", id, AuditHistory.ActionType.DELETE,
                holiday, null, authContextService.getCurrentStaff().getId());

        holiday.setIsActive(false);
        holidayRepository.save(holiday);
    }

    private HolidayResponse toResponse(Holiday holiday) {
        return HolidayResponse.builder()
                .id(holiday.getId())
                .name(holiday.getName())
                .holidayDate(holiday.getHolidayDate())
                .year(holiday.getYear())
                .isNationalHoliday(holiday.getIsNationalHoliday())
                .description(holiday.getDescription())
                .isActive(holiday.getIsActive())
                .createdAt(holiday.getCreatedAt())
                .updatedAt(holiday.getUpdatedAt())
                .build();
    }
}
