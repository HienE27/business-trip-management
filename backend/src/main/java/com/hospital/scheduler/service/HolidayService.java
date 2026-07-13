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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    public Page<HolidayResponse> getHolidaysPage(Pageable pageable) {
        return holidayRepository
                .findAll(PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "holidayDate")))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public HolidayResponse getHolidayById(Integer id) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngày lễ với ID: " + id));
        return toResponse(holiday);
    }

    public HolidayResponse createHoliday(HolidayRequest request) {
        // BUGFIX (was BE#16): the previous check used existsByHolidayDate which
        // matched both active and inactive rows. After soft-delete
        // (isActive=false) re-creating the same date was blocked because the
        // soft-deleted row was still in the table. The unique constraint at
        // the DB level means we still can't insert a second active row, so we
        // have two paths:
        //   1. An active row already exists → reject (semantic duplicate).
        //   2. Only inactive rows exist → reactivate the most recent one
        //      (upsert) so users can resurrect a soft-deleted holiday without
        //      hitting a UNIQUE constraint violation.
        if (holidayRepository.existsByHolidayDateAndIsActiveTrue(request.getHolidayDate())) {
            throw new BadRequestException("Ngày lễ '" + request.getHolidayDate() + "' đã tồn tại (đang hoạt động)");
        }

        java.util.List<Holiday> inactiveMatches = holidayRepository.findInactiveByHolidayDate(request.getHolidayDate());
        if (!inactiveMatches.isEmpty()) {
            // Reactivate the most recent soft-deleted row instead of inserting a fresh one.
            Holiday existing = inactiveMatches.get(0);
            Holiday before = cloneForAudit(existing);
            existing.setName(request.getName());
            existing.setYear(request.getHolidayDate().getYear());
            existing.setIsNationalHoliday(request.getIsNationalHoliday() != null ? request.getIsNationalHoliday() : false);
            existing.setDescription(request.getDescription());
            existing.setIsActive(true);
            Holiday reactivated = holidayRepository.save(existing);

            auditHistoryService.logAction("holiday", reactivated.getId(),
                    AuditHistory.ActionType.UPDATE, before, reactivated,
                    authContextService.getCurrentStaff().getId());

            notificationService.createNotificationForAllStaff("Ngày nghỉ lễ được kích hoạt lại",
                    "Ngày " + reactivated.getHolidayDate() + " đã được kích hoạt lại: " + reactivated.getName());

            return toResponse(reactivated);
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
                && holidayRepository.existsByHolidayDateAndIsActiveTrue(request.getHolidayDate())) {
            throw new BadRequestException("Ngày lễ '" + request.getHolidayDate() + "' đã tồn tại (đang hoạt động)");
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

    /**
     * BUGFIX (was BE#16) helper: produce a snapshot of a Holiday for audit
     * logging without re-using the live entity. Used when reactivating a
     * soft-deleted row so the audit_history entry has the pre-state.
     */
    private Holiday cloneForAudit(Holiday source) {
        return Holiday.builder()
                .id(source.getId())
                .name(source.getName())
                .holidayDate(source.getHolidayDate())
                .year(source.getYear())
                .isNationalHoliday(source.getIsNationalHoliday())
                .description(source.getDescription())
                .isActive(source.getIsActive())
                .build();
    }
}
