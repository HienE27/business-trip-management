package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.NotificationDTO;
import com.hospital.scheduler.dto.request.SchedulePeriodRequest;
import com.hospital.scheduler.dto.response.BulkPeriodResponse;
import com.hospital.scheduler.dto.response.ConflictCheckResponse;
import com.hospital.scheduler.dto.response.SchedulePeriodResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.CompensationDay;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.CompensationDayRepository;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SchedulePeriodService {

    private final SchedulePeriodRepository periodRepository;
    private final ScheduleRepository scheduleRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final StaffRepository staffRepository;
    private final AuditHistoryService auditHistoryService;
    private final ConflictDetectionService conflictDetectionService;
    private final NotificationService notificationService;
    private final EmailService emailService;

    public List<SchedulePeriodResponse> getAllPeriods() {
        return periodRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<SchedulePeriodResponse> getPeriodsByStatus(SchedulePeriod.PeriodStatus status) {
        return periodRepository.findByStatusOrderByStartDateDesc(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public SchedulePeriodResponse getPeriodById(Integer id) {
        SchedulePeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + id));
        return toResponse(period);
    }

    public SchedulePeriodResponse createPeriod(SchedulePeriodRequest request, Integer generatedById) {
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BadRequestException("Ngày bắt đầu phải trước ngày kết thúc");
        }

        Staff generatedBy = null;
        if (generatedById != null) {
            generatedBy = staffRepository.findById(generatedById).orElse(null);
        }

        SchedulePeriod period = SchedulePeriod.builder()
                .periodName(request.getPeriodName())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(SchedulePeriod.PeriodStatus.DRAFT)
                .generatedBy(generatedBy)
                .generatedAt(LocalDateTime.now())
                .build();

        SchedulePeriod saved = periodRepository.save(period);
        auditHistoryService.logAction("schedule_period", saved.getId(), AuditHistory.ActionType.INSERT, null, saved, null);
        return toResponse(saved);
    }

    public SchedulePeriodResponse updatePeriod(Integer id, SchedulePeriodRequest request) {
        SchedulePeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + id));

        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể sửa kỳ lịch ở trạng thái DRAFT");
        }

        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BadRequestException("Ngày bắt đầu phải trước ngày kết thúc");
        }

        SchedulePeriod prev = period;
        period.setPeriodName(request.getPeriodName());
        period.setStartDate(request.getStartDate());
        period.setEndDate(request.getEndDate());

        SchedulePeriod saved = periodRepository.save(period);
        auditHistoryService.logAction("schedule_period", id, AuditHistory.ActionType.UPDATE, prev, saved, null);
        return toResponse(saved);
    }

    public SchedulePeriodResponse publishPeriod(Integer id, Integer publishedById) {
        SchedulePeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + id));

        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể công bố kỳ lịch ở trạng thái DRAFT");
        }

        ConflictCheckResponse conflictCheck = conflictDetectionService.checkPeriodConflicts(id);
        if (conflictCheck.isHasConflicts()) {
            String msg = conflictCheck.getConflicts().stream()
                    .map(c -> c.getStaffName() + " (" + c.getWorkDate() + "): " + String.join(", ", c.getConflictReasons()))
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Có xung đột");
            throw new BadRequestException("Kỳ lịch có xung đột, không thể công bố: " + msg);
        }

        // Warn if there are coverage gaps but do not block publication
        if (conflictCheck.isHasCoverageGaps()) {
            org.slf4j.LoggerFactory.getLogger(SchedulePeriodService.class)
                    .warn("Publishing period {} with {} coverage gaps: {}",
                            id, conflictCheck.getTotalCoverageGaps(), conflictCheck.getCoverageGaps());
        }

        Staff publishedBy = null;
        if (publishedById != null) {
            publishedBy = staffRepository.findById(publishedById).orElse(null);
        }

        period.setStatus(SchedulePeriod.PeriodStatus.PUBLISHED);
        period.setPublishedBy(publishedBy != null ? publishedBy.getUsername() : null);
        period.setPublishedAt(LocalDateTime.now());
        SchedulePeriod saved = periodRepository.save(period);

        List<Schedule> periodSchedules = scheduleRepository.findByPeriodId(saved.getId());
        List<CompensationDay> periodCompDays = compensationDayRepository.findByPeriodId(saved.getId());

        // Send per-staff notifications with individual schedule details
        List<Staff> activeStaff = staffRepository.findByIsActiveTrue();
        for (Staff staff : activeStaff) {
            List<Schedule> staffSchedules = periodSchedules.stream()
                    .filter(s -> s.getStaff().getId().equals(staff.getId()))
                    .toList();
            List<CompensationDay> staffCompDays = periodCompDays.stream()
                    .filter(cd -> cd.getStaff().getId().equals(staff.getId()))
                    .toList();

            String dutyList = staffSchedules.stream()
                    .map(s -> s.getWorkDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " (" + s.getShiftType().getName() + ")")
                    .collect(Collectors.joining("; "));
            String compList = staffCompDays.stream()
                    .map(cd -> cd.getCompensationDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                    .collect(Collectors.joining(", "));

            String notifMsg = "Kỳ lịch \"" + saved.getPeriodName() + "\" (" + saved.getStartDate() + " - " + saved.getEndDate() + ") đã được công bố.\n" +
                    "Danh sách trực của bạn: " + (dutyList.isEmpty() ? "không có" : dutyList) + "\n" +
                    "Ngày nghỉ bù: " + (compList.isEmpty() ? "không có" : compList);
            notificationService.createNotification(staff.getId(),
                    new NotificationDTO("Lịch công tác đã được công bố", notifMsg));
        }

        // Send email notifications to all active staff with individual details
        emailService.sendSchedulePublishedEmail(activeStaff, period.getPeriodName(),
                period.getStartDate(), period.getEndDate(), periodSchedules, periodCompDays);

        return toResponse(saved);
    }

    public SchedulePeriodResponse archivePeriod(Integer id) {
        SchedulePeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + id));

        if (period.getStatus() != SchedulePeriod.PeriodStatus.PUBLISHED) {
            throw new BadRequestException("Chỉ có thể lưu trữ kỳ lịch ở trạng thái PUBLISHED");
        }

        period.setStatus(SchedulePeriod.PeriodStatus.ARCHIVED);
        return toResponse(periodRepository.save(period));
    }

    public BulkPeriodResponse bulkPublish(List<Integer> periodIds, Integer publishedById) {
        List<BulkPeriodResponse.PeriodResult> results = periodIds.stream()
                .map(id -> publishSingleResult(id, publishedById))
                .toList();
        return BulkPeriodResponse.of(results);
    }

    private BulkPeriodResponse.PeriodResult publishSingleResult(Integer id, Integer publishedById) {
        try {
            SchedulePeriodResponse published = publishPeriod(id, publishedById);
            return BulkPeriodResponse.PeriodResult.builder()
                    .id(id)
                    .periodName(published.getPeriodName())
                    .success(true)
                    .message("Công bố thành công")
                    .data(published)
                    .processedAt(java.time.LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            return BulkPeriodResponse.PeriodResult.builder()
                    .id(id)
                    .success(false)
                    .message(e.getMessage())
                    .processedAt(java.time.LocalDateTime.now())
                    .build();
        }
    }

    public BulkPeriodResponse bulkArchive(List<Integer> periodIds) {
        List<BulkPeriodResponse.PeriodResult> results = periodIds.stream()
                .map(this::archiveSingleResult)
                .toList();
        return BulkPeriodResponse.of(results);
    }

    private BulkPeriodResponse.PeriodResult archiveSingleResult(Integer id) {
        try {
            SchedulePeriodResponse archived = archivePeriod(id);
            return BulkPeriodResponse.PeriodResult.builder()
                    .id(id)
                    .periodName(archived.getPeriodName())
                    .success(true)
                    .message("Lưu trữ thành công")
                    .data(archived)
                    .processedAt(java.time.LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            return BulkPeriodResponse.PeriodResult.builder()
                    .id(id)
                    .success(false)
                    .message(e.getMessage())
                    .processedAt(java.time.LocalDateTime.now())
                    .build();
        }
    }

    public void deletePeriod(Integer id) {
        SchedulePeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + id));

        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể xóa kỳ lịch ở trạng thái DRAFT");
        }

        auditHistoryService.logAction("schedule_period", id, AuditHistory.ActionType.DELETE, period, null, null);
        periodRepository.delete(period);
    }

    private SchedulePeriodResponse toResponse(SchedulePeriod period) {
        SchedulePeriodResponse.StaffSummary generatedBySummary = null;
        if (period.getGeneratedBy() != null) {
            generatedBySummary = SchedulePeriodResponse.StaffSummary.builder()
                    .id(period.getGeneratedBy().getId())
                    .fullName(period.getGeneratedBy().getFullName())
                    .build();
        }

        return SchedulePeriodResponse.builder()
                .id(period.getId())
                .periodName(period.getPeriodName())
                .startDate(period.getStartDate())
                .endDate(period.getEndDate())
                .status(period.getStatus().name())
                .generatedBy(generatedBySummary)
                .generatedAt(period.getGeneratedAt())
                .publishedBy(period.getPublishedBy())
                .publishedAt(period.getPublishedAt())
                .createdAt(period.getCreatedAt())
                .updatedAt(period.getUpdatedAt())
                .build();
    }
}
