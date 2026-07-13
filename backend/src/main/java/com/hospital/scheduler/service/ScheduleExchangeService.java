package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.CSPScheduler;
import com.hospital.scheduler.algorithm.ScheduleChange;
import com.hospital.scheduler.algorithm.SchedulingResult;
import com.hospital.scheduler.algorithm.ShiftRequirementInfo;
import com.hospital.scheduler.dto.request.ScheduleExchangeDTO;
import com.hospital.scheduler.dto.response.ScheduleExchangeResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.CompensationDay;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.ScheduleExchange;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.dto.request.NotificationDTO;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
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
    private final ShiftRequirementRepository shiftRequirementRepository;
    private final CSPScheduler cspScheduler;
    private final SchedulingResultLoader schedulingResultLoader;

    public List<ScheduleExchangeResponse> getAllExchanges() {
        return exchangeRepository.findAll().stream()
                .map(ScheduleExchangeResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public Page<ScheduleExchangeResponse> getExchangesPage(Pageable pageable) {
        return exchangeRepository.findAll(pageable)
                .map(ScheduleExchangeResponse::fromEntity);
    }

    public java.util.Map<String, Long> getStatusCounts() {
        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        for (ScheduleExchange.ExchangeStatus status : ScheduleExchange.ExchangeStatus.values()) {
            counts.put(status.name(), exchangeRepository.countByStatus(status));
        }
        counts.put("total", exchangeRepository.count());
        return counts;
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

        // P8: extracted validation phase. Throws BadRequestException if any
        // constraint is violated. Returns the inputs needed for the swap.
        SwapContext ctx = validateSwapConstraints(exchange, requesterSchedule, targetSchedule);

        // P8: extracted execution phase — deletes old comp days, swaps staff,
        // creates new comp days, persists atomically.
        executeSwap(ctx, reviewerId);

        // P8: validate NEW compensation days do not conflict with existing schedules.
        validateCompensationConflicts(ctx);

        // P8: finalize: mark exchange APPROVED + audit + notify
        finalizeApproval(exchange, ctx, reviewer, reviewerId, reviewNote);

        return ScheduleExchangeResponse.fromEntity(exchangeRepository.save(exchange));
    }

    /**
     * Holds the inputs that flow from {@link #validateSwapConstraints} to
     * {@link #executeSwap}. Avoids passing 6+ parameters around.
     */
    private record SwapContext(
            Schedule requesterSchedule,
            Schedule targetSchedule,
            Staff requesterOldStaff,
            Staff targetOldStaff,
            LocalDate requesterWorkDate,
            LocalDate targetWorkDate,
            SchedulePeriod period,
            boolean requesterIsL01,
            boolean targetIsL01) {}

    /**
     * P8: phase 1 — verify the swap is allowed.
     * Checks: period status, leaves on both sides, comp days on both sides,
     * post-swap conflict detection for both staff members.
     */
    private SwapContext validateSwapConstraints(ScheduleExchange exchange,
                                                Schedule requesterSchedule,
                                                Schedule targetSchedule) {
        Staff requesterOldStaff = requesterSchedule.getStaff();
        Staff targetOldStaff = targetSchedule.getStaff();
        LocalDate requesterWorkDate = requesterSchedule.getWorkDate();
        LocalDate targetWorkDate = targetSchedule.getWorkDate();

        if (requesterSchedule.getPeriod().getStatus() != SchedulePeriod.PeriodStatus.PUBLISHED) {
            throw new BadRequestException("Không thể duyệt đổi ca khi kỳ lịch chưa được công bố");
        }

        List<LeaveRequest> targetLeaves = leaveRequestRepository.findByStaffIdAndDateRange(
                targetOldStaff.getId(), targetWorkDate, targetWorkDate);
        if (targetLeaves.stream().anyMatch(l -> l.getStatus() == LeaveRequest.LeaveStatus.APPROVED)) {
            throw new BadRequestException("Nhân sự được đổi (" + targetOldStaff.getFullName() + ") có ngày nghỉ phép được duyệt vào ngày " + targetWorkDate);
        }
        List<LeaveRequest> requesterLeaves = leaveRequestRepository.findByStaffIdAndDateRange(
                requesterOldStaff.getId(), requesterWorkDate, requesterWorkDate);
        if (requesterLeaves.stream().anyMatch(l -> l.getStatus() == LeaveRequest.LeaveStatus.APPROVED)) {
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

        return new SwapContext(
                requesterSchedule, targetSchedule,
                requesterOldStaff, targetOldStaff,
                requesterWorkDate, targetWorkDate,
                requesterSchedule.getPeriod(),
                requesterIsL01, targetIsL01);
    }

    /**
     * P8: phase 2 — perform the swap atomically:
     *  1. Delete existing comp days for affected staff + dates (audit each).
     *  2. Copy schedule rows to new staff (preserving FK + audit trail).
     *  3. Create new comp days for the new L01 assignments (audit each).
     */
    private void executeSwap(SwapContext ctx, Integer reviewerId) {
        // Delete existing compensation days for affected staff + date combinations
        if (ctx.requesterIsL01()) {
            compensationDayRepository.findByStaffIdAndCompensationDate(
                    ctx.requesterOldStaff().getId(), ctx.requesterWorkDate())
                    .ifPresent(cd -> {
                        auditHistoryService.logAction("compensation_day", cd.getId(), AuditHistory.ActionType.DELETE,
                                cd, null, reviewerId);
                        compensationDayRepository.delete(cd);
                    });
        }
        if (ctx.targetIsL01()) {
            compensationDayRepository.findByStaffIdAndCompensationDate(
                    ctx.targetOldStaff().getId(), ctx.targetWorkDate())
                    .ifPresent(cd -> {
                        auditHistoryService.logAction("compensation_day", cd.getId(), AuditHistory.ActionType.DELETE,
                                cd, null, reviewerId);
                        compensationDayRepository.delete(cd);
                    });
        }

        // Swap staff on schedules (in-memory)
        ctx.requesterSchedule().setStaff(ctx.targetOldStaff());
        ctx.targetSchedule().setStaff(ctx.requesterOldStaff());

        // Create new compensation days for the new L01 assignments
        SchedulePeriod period = ctx.period();
        if (ctx.targetIsL01()) {
            CompensationDay newCompForRequester = CompensationDay.builder()
                    .schedule(ctx.requesterSchedule())
                    .staff(ctx.requesterOldStaff())
                    .period(period)
                    .shiftDate(ctx.targetWorkDate())
                    .compensationDate(compensationDateCalculator.calculate(ctx.targetWorkDate()))
                    .note("Ngày nghỉ bù từ đổi ca: " + ctx.targetOldStaff().getFullName() + " -> " + ctx.requesterOldStaff().getFullName())
                    .build();
            CompensationDay savedCompForRequester = compensationDayRepository.save(newCompForRequester);
            auditHistoryService.logAction("compensation_day", savedCompForRequester.getId(), AuditHistory.ActionType.INSERT,
                    null, savedCompForRequester, reviewerId);
        }
        if (ctx.requesterIsL01()) {
            CompensationDay newCompForTarget = CompensationDay.builder()
                    .schedule(ctx.targetSchedule())
                    .staff(ctx.targetOldStaff())
                    .period(period)
                    .shiftDate(ctx.requesterWorkDate())
                    .compensationDate(compensationDateCalculator.calculate(ctx.requesterWorkDate()))
                    .note("Ngày nghỉ bù từ đổi ca: " + ctx.requesterOldStaff().getFullName() + " -> " + ctx.targetOldStaff().getFullName())
                    .build();
            CompensationDay savedCompForTarget = compensationDayRepository.save(newCompForTarget);
            auditHistoryService.logAction("compensation_day", savedCompForTarget.getId(), AuditHistory.ActionType.INSERT,
                    null, savedCompForTarget, reviewerId);
        }

        // BUGFIX (was #5): Plain setStaff + save tripped the unique constraint when
        // both schedules land on the same slot post-swap. Strategy: copy row into
        // fresh entity, delete old, save new. CompensationDay.schedule FK is
        // rewired to the new schedule id before the old row is deleted.
        copyCompensationFkAndDelete(ctx.requesterSchedule(), ctx.targetOldStaff(), ctx.requesterWorkDate(),
                period, reviewerId);
        copyCompensationFkAndDelete(ctx.targetSchedule(), ctx.requesterOldStaff(), ctx.targetWorkDate(),
                period, reviewerId);
        log.debug("Schedule swap persisted atomically for exchange");
    }

    /**
     * P8: phase 3 — validate the NEW compensation days don't collide with
     * existing schedules. Throws on collision (rolls back the swap).
     */
    private void validateCompensationConflicts(SwapContext ctx) {
        if (ctx.targetIsL01()) {
            LocalDate newCompForRequesterDate = compensationDateCalculator.calculate(ctx.targetWorkDate());
            List<Schedule> reqSchedulesOnCompDate = scheduleRepository.findByStaffIdAndWorkDate(
                    ctx.requesterOldStaff().getId(), newCompForRequesterDate);
            if (!reqSchedulesOnCompDate.isEmpty()) {
                throw new BadRequestException("Ngày nghỉ bù mới của " + ctx.requesterOldStaff().getFullName()
                        + " (" + newCompForRequesterDate + ") bị xung đột với lịch hiện có: "
                        + reqSchedulesOnCompDate.get(0).getShiftType().getName());
            }
        }
        if (ctx.requesterIsL01()) {
            LocalDate newCompForTargetDate = compensationDateCalculator.calculate(ctx.requesterWorkDate());
            List<Schedule> tgtSchedulesOnCompDate = scheduleRepository.findByStaffIdAndWorkDate(
                    ctx.targetOldStaff().getId(), newCompForTargetDate);
            if (!tgtSchedulesOnCompDate.isEmpty()) {
                throw new BadRequestException("Ngày nghỉ bù mới của " + ctx.targetOldStaff().getFullName()
                        + " (" + newCompForTargetDate + ") bị xung đột với lịch hiện có: "
                        + tgtSchedulesOnCompDate.get(0).getShiftType().getName());
            }
        }
    }

    /**
     * P8: phase 4 — finalize the approved exchange: update status, audit,
     * notify both staff, send email, run post-swap CSP re-solve.
     */
    private void finalizeApproval(ScheduleExchange exchange, SwapContext ctx,
                                  Staff reviewer, Integer reviewerId, String reviewNote) {
        exchange.setStatus(ScheduleExchange.ExchangeStatus.APPROVED);
        exchange.setReviewedBy(reviewer);
        exchange.setReviewedAt(LocalDateTime.now());
        exchange.setReviewNote(reviewNote);

        ScheduleExchange saved = exchangeRepository.save(exchange);
        auditHistoryService.logAction("schedule_exchange", saved.getId(), AuditHistory.ActionType.APPROVE,
                "PENDING", saved, reviewerId);

        String approveMsg = "Yêu cầu đổi trực ngày " + ctx.requesterSchedule().getWorkDate()
                + " <-> " + ctx.targetSchedule().getWorkDate()
                + " đã được duyệt bởi " + reviewer.getFullName() + ".";
        notificationService.createNotification(ctx.requesterOldStaff().getId(),
                new NotificationDTO("Yêu cầu đổi trực đã được duyệt", approveMsg));
        notificationService.createNotification(ctx.targetOldStaff().getId(),
                new NotificationDTO("Yêu cầu đổi trực đã được duyệt", approveMsg));

        emailService.sendSwapApprovedEmail(ctx.requesterOldStaff(),
                ctx.targetSchedule().getWorkDate().toString(),
                ctx.requesterSchedule().getShiftType().getName());
        emailService.sendSwapApprovedEmail(ctx.targetOldStaff(),
                ctx.requesterSchedule().getWorkDate().toString(),
                ctx.targetSchedule().getShiftType().getName());

        // Post-swap incremental re-solve: confirm period is still feasible
        rescheduleAfterSwap(ctx.period(),
                ctx.requesterOldStaff().getId(), ctx.targetOldStaff().getId(),
                ctx.requesterSchedule(), ctx.targetSchedule());
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
        auditHistoryService.logAction("schedule_exchange", exchangeId, AuditHistory.ActionType.REJECT,
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
        auditHistoryService.logAction("schedule_exchange", exchangeId, AuditHistory.ActionType.CANCEL,
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

    /**
     * Re-validate the period after a swap with the {@link CSPScheduler}
     * incremental resolver. Throws {@link BadRequestException} if the swap
     * pushes the period out of feasibility so the caller can roll back.
     */
    private void rescheduleAfterSwap(
            SchedulePeriod period,
            Integer requesterOldStaffId,
            Integer targetOldStaffId,
            Schedule requesterSchedule,
            Schedule targetSchedule) {
        try {
            SchedulingResult previous = schedulingResultLoader.loadPreviousFromDb(
                    period.getId(), scheduleRepository);
            List<ShiftRequirementInfo> requirements = AutoSchedulingService.toRequirementInfos(
                    shiftRequirementRepository.findByPeriodId(period.getId()));
            List<LeaveRequest> leaves = leaveRequestRepository.findApprovedInRange(
                    period.getStartDate(), period.getEndDate());
            List<Staff> activeStaff = new ArrayList<>(staffRepository.findByIsActiveTrue());

            ScheduleChange.AssignmentDelta requesterSide = ScheduleChange.AssignmentDelta.builder()
                    .staffId(requesterOldStaffId)
                    .date(requesterSchedule.getWorkDate())
                    .shiftType(requesterSchedule.getShiftType().getId())
                    .oldStaffId(targetOldStaffId)
                    .build();
            ScheduleChange.AssignmentDelta targetSide = ScheduleChange.AssignmentDelta.builder()
                    .staffId(targetOldStaffId)
                    .date(targetSchedule.getWorkDate())
                    .shiftType(targetSchedule.getShiftType().getId())
                    .oldStaffId(requesterOldStaffId)
                    .build();
            ScheduleChange changes = ScheduleChange.builder()
                    .modified(new ArrayList<>(List.of(requesterSide, targetSide)))
                    .build();

            SchedulingResult result = cspScheduler.reSolve(previous, changes, activeStaff, requirements, leaves);
            if (result == null || !result.isValid()) {
                String reason = result == null ? "no result" : String.join("; ", result.getErrors());
                throw new BadRequestException("Đổi ca làm period không còn feasible: " + reason);
            }
            log.debug("Post-swap re-solve valid for period {}", period.getId());
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            // BUGFIX (was #6): silently logging and continuing broke the swap
            // rollback contract — the surrounding @Transactional would commit the
            // schedule swap even when the period became infeasible. Re-throw as a
            // domain BadRequestException so Spring rolls back the entire transaction
            // atomically and the swap is rejected, matching the documented contract.
            log.warn("Post-swap re-solve failed for period {} ({}): {}",
                    period.getId(), e.getClass().getSimpleName(), e.getMessage());
            throw new BadRequestException(
                    "Đổi ca làm period không còn feasible sau khi đổi: " + e.getMessage());
        }
    }

    /**
     * Swap a schedule's staff by recreating the row — required to side-step the
     * (period_id, staff_id, shift_type_id, work_date) UNIQUE constraint when both
     * sides of the swap target the same slot. The old row is deleted only after the
     * new row is persisted and any compensation-day FK is rewired.
     *
     * <p>Atomicity: both the new schedule insert and the old row delete happen in
     * the surrounding {@code @Transactional}. A failure on the second delete (after
     * the first insert) rolls back the first insert automatically.
     */
    private void copyCompensationFkAndDelete(Schedule original, Staff newStaff,
                                             LocalDate workDate, SchedulePeriod period,
                                             Integer reviewerId) {
        // Build the swapped row preserving all immutable fields (id is intentionally omitted).
        Schedule replacement = Schedule.builder()
                .period(original.getPeriod())
                .staff(newStaff)
                .shiftType(original.getShiftType())
                .workDate(workDate)
                .requirement(original.getRequirement())
                .hasConflict(false)
                .isPreview(original.getIsPreview())
                .build();
        Schedule saved = scheduleRepository.save(replacement);

        // Rewire any compensation_day rows that referenced the old schedule id so
        // the FK doesn't block the delete. findByScheduleId returns a List (0..n).
        List<CompensationDay> compDays = compensationDayRepository.findByScheduleId(original.getId());
        for (CompensationDay cd : compDays) {
            cd.setSchedule(saved);
            compensationDayRepository.save(cd);
        }

        scheduleRepository.delete(original);
        // Best-effort audit — never fail the swap for an audit miss.
        // Use original.getId() for the log key so the message is always safe to build
        // even if saved is null (which can happen if save() returns null for a
        // transient entity in some JPA implementations).
        int logKey = original.getId();
        try {
            if (saved != null) {
                auditHistoryService.logAction("schedule", saved.getId(),
                        AuditHistory.ActionType.UPDATE, original, saved, reviewerId);
            }
        } catch (Exception auditEx) {
            log.warn("Audit for schedule swap (id={}) skipped: {}", logKey, auditEx.getMessage());
        }
    }
}
