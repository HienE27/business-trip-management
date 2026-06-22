package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.ScheduleExchangeDTO;
import com.hospital.scheduler.dto.response.ScheduleExchangeResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.CompensationDay;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.ScheduleExchange;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.dto.request.NotificationDTO;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleExchangeService {

    private final ScheduleExchangeRepository exchangeRepository;
    private final ScheduleRepository scheduleRepository;
    private final StaffRepository staffRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final AuditHistoryService auditHistoryService;
    private final ConflictDetectionService conflictDetectionService;
    private final CompensationDateCalculator compensationDateCalculator;
    private final NotificationService notificationService;
    private final EmailService emailService;

    public List<ScheduleExchangeResponse> getAllExchanges() {
        return exchangeRepository.findAll().stream()
                .map(ScheduleExchangeResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ScheduleExchangeResponse> getExchangesByRequester(Integer requesterId) {
        return exchangeRepository.findByRequesterId(requesterId).stream()
                .map(ScheduleExchangeResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ScheduleExchangeResponse> getExchangesByTarget(Integer targetId) {
        return exchangeRepository.findByTargetId(targetId).stream()
                .map(ScheduleExchangeResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ScheduleExchangeResponse> getPendingExchanges() {
        return exchangeRepository.findByStatus(ScheduleExchange.ExchangeStatus.PENDING).stream()
                .map(ScheduleExchangeResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ScheduleExchangeResponse> getExchangesByStatus(ScheduleExchange.ExchangeStatus status) {
        return exchangeRepository.findByStatus(status).stream()
                .map(ScheduleExchangeResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ScheduleExchangeResponse> getExchangesForUser(Integer userId) {
        return exchangeRepository.findAllByUserId(userId).stream()
                .map(ScheduleExchangeResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public ScheduleExchangeResponse getExchangeById(Integer id) {
        ScheduleExchange exchange = exchangeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu đổi ca với ID: " + id));
        return ScheduleExchangeResponse.fromEntity(exchange);
    }

    public ScheduleExchangeResponse createExchange(Integer requesterId, ScheduleExchangeDTO dto) {
        Staff requester = staffRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự yêu cầu với ID: " + requesterId));

        Schedule requesterSchedule = scheduleRepository.findById(dto.getRequesterScheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch của người yêu cầu với ID: " + dto.getRequesterScheduleId()));

        Schedule targetSchedule = scheduleRepository.findById(dto.getTargetScheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch của người được đổi với ID: " + dto.getTargetScheduleId()));

        if (!requesterSchedule.getStaff().getId().equals(requesterId)) {
            throw new BadRequestException("Lịch yêu cầu không thuộc về người gửi");
        }

        if (requesterSchedule.getId().equals(targetSchedule.getId())) {
            throw new BadRequestException("Không thể tự đổi ca với chính mình");
        }

        if (requesterSchedule.getStaff().getId().equals(targetSchedule.getStaff().getId())) {
            throw new BadRequestException("Không thể đổi ca giữa cùng một nhân sự");
        }

        Staff targetStaff = targetSchedule.getStaff();

        if (!Boolean.TRUE.equals(targetStaff.getIsActive())) {
            throw new BadRequestException("Nhân sự được đổi đang ngừng hoạt động, không thể đổi ca");
        }

        if (requesterSchedule.getPeriod().getStatus() != SchedulePeriod.PeriodStatus.PUBLISHED) {
            throw new BadRequestException("Không thể đổi ca khi kỳ lịch chưa được công bố");
        }

        // Verify same period
        if (!requesterSchedule.getPeriod().getId().equals(targetSchedule.getPeriod().getId())) {
            throw new BadRequestException("Hai lịch phải thuộc cùng một kỳ lịch.");
        }

        // Business rule: same shift type required for exchange (L01↔L01, L02↔L02, L03↔L03, L04↔L04)
        String requesterShiftType = requesterSchedule.getShiftType().getId();
        String targetShiftType = targetSchedule.getShiftType().getId();
        if (!requesterShiftType.equals(targetShiftType)) {
            throw new BadRequestException(
                    "Chỉ có thể đổi ca cùng loại. Ca của bạn (" + requesterShiftType + ") khác loại với ca được đổi (" + targetShiftType + ").");
        }

        compensationDayRepository.findByStaffIdAndCompensationDate(requesterId, targetSchedule.getWorkDate())
                .ifPresent(cd -> {
                    throw new BadRequestException("Không thể đổi ca: nhân sự yêu cầu có ngày nghỉ bù vào ngày " + targetSchedule.getWorkDate());
                });

        compensationDayRepository.findByStaffIdAndCompensationDate(targetStaff.getId(), requesterSchedule.getWorkDate())
                .ifPresent(cd -> {
                    throw new BadRequestException("Không thể đổi ca: nhân sự được đổi có ngày nghỉ bù vào ngày " + requesterSchedule.getWorkDate());
                });

        ScheduleExchange exchange = ScheduleExchange.builder()
                .period(requesterSchedule.getPeriod())
                .requester(requester)
                .target(targetSchedule.getStaff())
                .requesterSchedule(requesterSchedule)
                .targetSchedule(targetSchedule)
                .reason(dto.getReason())
                .status(ScheduleExchange.ExchangeStatus.PENDING)
                .build();

        ScheduleExchange saved = exchangeRepository.save(exchange);
        auditHistoryService.logAction("schedule_exchange", saved.getId(), AuditHistory.ActionType.INSERT, null, saved, requesterId);

        // Notify the target staff about the exchange request
        notificationService.createNotification(targetSchedule.getStaff().getId(),
                new NotificationDTO("Yêu cầu đổi trực mới",
                        "Nhân sự " + requester.getFullName() + " yêu cầu đổi trực ngày " + requesterSchedule.getWorkDate() + " với bạn. Vui lòng kiểm tra và chờ quản lý duyệt."));

        // Notify all managers about the pending exchange request for review
        List<Staff> managers = staffRepository.findManagers();
        for (Staff manager : managers) {
            if (!manager.getId().equals(requesterId) && !manager.getId().equals(targetStaff.getId())) {
                notificationService.createNotification(manager.getId(),
                        new NotificationDTO("Yêu cầu đổi trực chờ duyệt",
                                "Nhân sự " + requester.getFullName() + " yêu cầu đổi trực với " + targetStaff.getFullName()
                                        + " ngày " + requesterSchedule.getWorkDate() + ". Vui lòng kiểm tra và duyệt."));
            }
        }

        return ScheduleExchangeResponse.fromEntity(saved);
    }

    @Transactional
    public ScheduleExchangeResponse approveExchange(Integer exchangeId, Integer reviewerId, String reviewNote) {
        ScheduleExchange exchange = exchangeRepository.findByIdWithLock(exchangeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu đổi ca với ID: " + exchangeId));

        Staff reviewer = staffRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người duyệt với ID: " + reviewerId));

        if (exchange.getStatus() != ScheduleExchange.ExchangeStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể duyệt yêu cầu đang chờ");
        }

        Schedule requesterSchedule = exchange.getRequesterSchedule();
        Schedule targetSchedule = exchange.getTargetSchedule();

        Staff requesterOldStaff = requesterSchedule.getStaff();
        Staff targetOldStaff = targetSchedule.getStaff();
        LocalDate requesterWorkDate = requesterSchedule.getWorkDate();
        LocalDate targetWorkDate = targetSchedule.getWorkDate();

        if (requesterSchedule.getPeriod().getStatus() != SchedulePeriod.PeriodStatus.PUBLISHED) {
            throw new BadRequestException("Không thể duyệt đổi ca khi kỳ lịch chưa được công bố");
        }

        List<LeaveRequest> targetLeaves = leaveRequestRepository.findByStaffIdAndDateRange(
                targetOldStaff.getId(), targetWorkDate, targetWorkDate);
        boolean targetHasApprovedLeave = targetLeaves.stream()
                .anyMatch(l -> l.getStatus() == LeaveRequest.LeaveStatus.APPROVED);
        if (targetHasApprovedLeave) {
            throw new BadRequestException("Nhân sự được đổi (" + targetOldStaff.getFullName() + ") có ngày nghỉ phép được duyệt vào ngày " + targetWorkDate);
        }

        List<LeaveRequest> requesterLeaves = leaveRequestRepository.findByStaffIdAndDateRange(
                requesterOldStaff.getId(), requesterWorkDate, requesterWorkDate);
        boolean requesterHasApprovedLeave = requesterLeaves.stream()
                .anyMatch(l -> l.getStatus() == LeaveRequest.LeaveStatus.APPROVED);
        if (requesterHasApprovedLeave) {
            throw new BadRequestException("Nhân sự yêu cầu (" + requesterOldStaff.getFullName() + ") có ngày nghỉ phép được duyệt vào ngày " + requesterWorkDate);
        }

        compensationDayRepository.findByStaffIdAndCompensationDate(targetOldStaff.getId(), targetWorkDate)
                .ifPresent(cd -> {
                    throw new BadRequestException("Nhân sự được đổi (" + targetOldStaff.getFullName() + ") có ngày nghỉ bù vào ngày " + targetWorkDate);
                });

        compensationDayRepository.findByStaffIdAndCompensationDate(requesterOldStaff.getId(), requesterWorkDate)
                .ifPresent(cd -> {
                    throw new BadRequestException("Nhân sự yêu cầu (" + requesterOldStaff.getFullName() + ") có ngày nghỉ bù vào ngày " + requesterWorkDate);
                });

        List<String> requesterConflicts = conflictDetectionService.detectAllConflicts(
                requesterOldStaff.getId(), targetWorkDate,
                requesterSchedule.getShiftType().getId(), targetSchedule.getId());
        if (!requesterConflicts.isEmpty()) {
            throw new BadRequestException("Nhân sự yêu cầu (" + requesterOldStaff.getFullName() + ") bị xung đột sau khi đổi: " + String.join("; ", requesterConflicts));
        }

        List<String> targetConflicts = conflictDetectionService.detectAllConflicts(
                targetOldStaff.getId(), requesterWorkDate,
                targetSchedule.getShiftType().getId(), requesterSchedule.getId());
        if (!targetConflicts.isEmpty()) {
            throw new BadRequestException("Nhân sự được đổi (" + targetOldStaff.getFullName() + ") bị xung đột sau khi đổi: " + String.join("; ", targetConflicts));
        }

        boolean requesterIsL01 = ConflictDetectionService.SHIFT_TYPE_L01.equals(requesterSchedule.getShiftType().getId());
        boolean targetIsL01 = ConflictDetectionService.SHIFT_TYPE_L01.equals(targetSchedule.getShiftType().getId());

        // Delete existing compensation days for affected staff + date combinations
        if (requesterIsL01) {
                    compensationDayRepository.findByStaffIdAndCompensationDate(requesterOldStaff.getId(), requesterWorkDate)
                    .ifPresent(cd -> {
                        auditHistoryService.logAction("compensation_day", cd.getId(), AuditHistory.ActionType.DELETE,
                                cd, null, reviewerId);
                        compensationDayRepository.delete(cd);
                    });
        }
        if (targetIsL01) {
            compensationDayRepository.findByStaffIdAndCompensationDate(targetOldStaff.getId(), targetWorkDate)
                    .ifPresent(cd -> {
                        auditHistoryService.logAction("compensation_day", cd.getId(), AuditHistory.ActionType.DELETE,
                                cd, null, reviewerId);
                        compensationDayRepository.delete(cd);
                    });
        }

        // Swap staff on schedules
        requesterSchedule.setStaff(targetOldStaff);
        targetSchedule.setStaff(requesterOldStaff);

        // Create new compensation days for the new L01 assignments
        SchedulePeriod period = requesterSchedule.getPeriod();
        if (targetIsL01) {
            CompensationDay newCompForRequester = CompensationDay.builder()
                    .schedule(requesterSchedule)
                    .staff(requesterOldStaff)
                    .period(period)
                    .shiftDate(targetWorkDate)
                    .compensationDate(compensationDateCalculator.calculate(targetWorkDate))
                    .note("Ngày nghỉ bù từ đổi ca: " + targetOldStaff.getFullName() + " -> " + requesterOldStaff.getFullName())
                    .build();
            CompensationDay savedCompForRequester = compensationDayRepository.save(newCompForRequester);
            auditHistoryService.logAction("compensation_day", savedCompForRequester.getId(), AuditHistory.ActionType.INSERT,
                    null, savedCompForRequester, reviewerId);
        }
        if (requesterIsL01) {
            CompensationDay newCompForTarget = CompensationDay.builder()
                    .schedule(targetSchedule)
                    .staff(targetOldStaff)
                    .period(period)
                    .shiftDate(requesterWorkDate)
                    .compensationDate(compensationDateCalculator.calculate(requesterWorkDate))
                    .note("Ngày nghỉ bù từ đổi ca: " + requesterOldStaff.getFullName() + " -> " + targetOldStaff.getFullName())
                    .build();
            CompensationDay savedCompForTarget = compensationDayRepository.save(newCompForTarget);
            auditHistoryService.logAction("compensation_day", savedCompForTarget.getId(), AuditHistory.ActionType.INSERT,
                    null, savedCompForTarget, reviewerId);
        }

        scheduleRepository.save(requesterSchedule);
        scheduleRepository.save(targetSchedule);

        // Validate NEW compensation days do not conflict with existing schedules
        if (targetIsL01) {
            LocalDate newCompForRequesterDate = compensationDateCalculator.calculate(targetWorkDate);
            List<Schedule> reqSchedulesOnCompDate = scheduleRepository.findByStaffIdAndWorkDate(requesterOldStaff.getId(), newCompForRequesterDate);
            if (!reqSchedulesOnCompDate.isEmpty()) {
                throw new BadRequestException("Ngày nghỉ bù mới của " + requesterOldStaff.getFullName()
                        + " (" + newCompForRequesterDate + ") bị xung đột với lịch hiện có: "
                        + reqSchedulesOnCompDate.get(0).getShiftType().getName());
            }
        }
        if (requesterIsL01) {
            LocalDate newCompForTargetDate = compensationDateCalculator.calculate(requesterWorkDate);
            List<Schedule> tgtSchedulesOnCompDate = scheduleRepository.findByStaffIdAndWorkDate(targetOldStaff.getId(), newCompForTargetDate);
            if (!tgtSchedulesOnCompDate.isEmpty()) {
                throw new BadRequestException("Ngày nghỉ bù mới của " + targetOldStaff.getFullName()
                        + " (" + newCompForTargetDate + ") bị xung đột với lịch hiện có: "
                        + tgtSchedulesOnCompDate.get(0).getShiftType().getName());
            }
        }

        exchange.setStatus(ScheduleExchange.ExchangeStatus.APPROVED);
        exchange.setReviewedBy(reviewer);
        exchange.setReviewedAt(LocalDateTime.now());
        exchange.setReviewNote(reviewNote);

        ScheduleExchange saved = exchangeRepository.save(exchange);
        auditHistoryService.logAction("schedule_exchange", exchangeId, AuditHistory.ActionType.UPDATE,
                "PENDING", saved, reviewerId);

        // Notify both staff about the approved exchange
        String approveMsg = "Yêu cầu đổi trực ngày " + requesterSchedule.getWorkDate() + " <-> " + targetSchedule.getWorkDate() + " đã được duyệt bởi " + reviewer.getFullName() + ".";
        notificationService.createNotification(requesterOldStaff.getId(),
                new NotificationDTO("Yêu cầu đổi trực đã được duyệt", approveMsg));
        notificationService.createNotification(targetOldStaff.getId(),
                new NotificationDTO("Yêu cầu đổi trực đã được duyệt", approveMsg));

        // Send email notifications to both staff
        emailService.sendSwapApprovedEmail(requesterOldStaff,
                targetSchedule.getWorkDate().toString(),
                requesterSchedule.getShiftType().getName());
        emailService.sendSwapApprovedEmail(targetOldStaff,
                requesterSchedule.getWorkDate().toString(),
                targetSchedule.getShiftType().getName());

        return ScheduleExchangeResponse.fromEntity(saved);
    }

    public ScheduleExchangeResponse rejectExchange(Integer exchangeId, Integer reviewerId, String reviewNote) {
        ScheduleExchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu đổi ca với ID: " + exchangeId));

        Staff reviewer = staffRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người duyệt với ID: " + reviewerId));

        if (exchange.getStatus() != ScheduleExchange.ExchangeStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể từ chối yêu cầu đang chờ");
        }

        exchange.setStatus(ScheduleExchange.ExchangeStatus.REJECTED);
        exchange.setReviewedBy(reviewer);
        exchange.setReviewedAt(LocalDateTime.now());
        exchange.setReviewNote(reviewNote);

        ScheduleExchange saved = exchangeRepository.save(exchange);
        auditHistoryService.logAction("schedule_exchange", exchangeId, AuditHistory.ActionType.UPDATE,
                "PENDING", saved, reviewerId);

        // Notify both staff about the rejected exchange
        String rejectMsg = "Yêu cầu đổi trực ngày " + exchange.getRequesterSchedule().getWorkDate() + " <-> " + exchange.getTargetSchedule().getWorkDate() + " đã bị từ chối bởi " + reviewer.getFullName()
                + (reviewNote != null && !reviewNote.isBlank() ? ". Lý do: " + reviewNote : "");
        notificationService.createNotification(exchange.getRequester().getId(),
                new NotificationDTO("Yêu cầu đổi trực bị từ chối", rejectMsg));
        notificationService.createNotification(exchange.getTarget().getId(),
                new NotificationDTO("Yêu cầu đổi trực bị từ chối", rejectMsg));

        // Send email notifications to both staff
        emailService.sendSwapRejectedEmail(exchange.getRequester(),
                exchange.getRequesterSchedule().getWorkDate().toString(),
                exchange.getTargetSchedule().getWorkDate().toString(),
                reviewNote);
        emailService.sendSwapRejectedEmail(exchange.getTarget(),
                exchange.getRequesterSchedule().getWorkDate().toString(),
                exchange.getTargetSchedule().getWorkDate().toString(),
                reviewNote);

        return ScheduleExchangeResponse.fromEntity(saved);
    }

    public ScheduleExchangeResponse cancelExchange(Integer exchangeId, Staff currentStaff) {
        ScheduleExchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu đổi ca với ID: " + exchangeId));

        boolean canCancel = exchange.getRequester().getId().equals(currentStaff.getId())
                || exchange.getTarget().getId().equals(currentStaff.getId())
                || currentStaff.getStaffRoles().stream()
                .map(role -> role.getRole() != null ? role.getRole().getName() : null)
                .anyMatch(roleName -> "ADMIN".equals(roleName) || "MANAGER".equals(roleName));
        if (!canCancel) {
            throw new BadRequestException("Bạn không có quyền hủy yêu cầu đổi ca này");
        }

        if (exchange.getStatus() != ScheduleExchange.ExchangeStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể hủy yêu cầu đang chờ");
        }

        exchange.setStatus(ScheduleExchange.ExchangeStatus.CANCELLED);

        ScheduleExchange saved = exchangeRepository.save(exchange);
        auditHistoryService.logAction("schedule_exchange", exchangeId, AuditHistory.ActionType.UPDATE,
                "PENDING", saved, currentStaff.getId());

        // Notify both staff about the cancellation
        notificationService.createNotification(exchange.getRequester().getId(),
                new NotificationDTO("Yêu cầu đổi trực đã bị hủy",
                        "Yêu cầu đổi trực ngày " + exchange.getRequesterSchedule().getWorkDate() + " <-> " + exchange.getTargetSchedule().getWorkDate() + " đã bị hủy."));
        notificationService.createNotification(exchange.getTarget().getId(),
                new NotificationDTO("Yêu cầu đổi trực đã bị hủy",
                        "Yêu cầu đổi trực ngày " + exchange.getRequesterSchedule().getWorkDate() + " <-> " + exchange.getTargetSchedule().getWorkDate() + " đã bị hủy."));

        return ScheduleExchangeResponse.fromEntity(saved);
    }
}
