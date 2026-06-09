package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.ScheduleExchangeDTO;
import com.hospital.scheduler.dto.response.ScheduleExchangeResponse;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.ScheduleExchange;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return exchangeRepository.findPendingByRequesterIdOrTargetId(userId, userId).stream()
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

        if (requesterSchedule.getPeriod().getStatus().name().equals("PUBLISHED")) {
            throw new BadRequestException("Không thể đổi ca khi kỳ lịch đã được công bố");
        }

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
        return ScheduleExchangeResponse.fromEntity(saved);
    }

    public ScheduleExchangeResponse approveExchange(Integer exchangeId, Integer reviewerId, String reviewNote) {
        ScheduleExchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu đổi ca với ID: " + exchangeId));

        Staff reviewer = staffRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người duyệt với ID: " + reviewerId));

        if (exchange.getStatus() != ScheduleExchange.ExchangeStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể duyệt yêu cầu đang chờ");
        }

        Schedule requesterSchedule = exchange.getRequesterSchedule();
        Schedule targetSchedule = exchange.getTargetSchedule();

        if (requesterSchedule.getPeriod().getStatus().name().equals("PUBLISHED")) {
            throw new BadRequestException("Không thể duyệt đổi ca khi kỳ lịch đã được công bố");
        }

        // Check if target staff has approved leave on the day they would receive
        List<LeaveRequest> targetLeaves = leaveRequestRepository.findByStaffIdAndDateRange(
                targetSchedule.getStaff().getId(), targetSchedule.getWorkDate(), targetSchedule.getWorkDate());
        boolean targetHasApprovedLeave = targetLeaves.stream()
                .anyMatch(l -> l.getStatus() == LeaveRequest.LeaveStatus.APPROVED);
        if (targetHasApprovedLeave) {
            throw new BadRequestException("Nhân sự được đổi (" + targetSchedule.getStaff().getFullName() + ") có ngày nghỉ phép được duyệt vào ngày " + targetSchedule.getWorkDate());
        }

        // Check if requester staff has approved leave on the day they would receive
        List<LeaveRequest> requesterLeaves = leaveRequestRepository.findByStaffIdAndDateRange(
                requesterSchedule.getStaff().getId(), requesterSchedule.getWorkDate(), requesterSchedule.getWorkDate());
        boolean requesterHasApprovedLeave = requesterLeaves.stream()
                .anyMatch(l -> l.getStatus() == LeaveRequest.LeaveStatus.APPROVED);
        if (requesterHasApprovedLeave) {
            throw new BadRequestException("Nhân sự yêu cầu (" + requesterSchedule.getStaff().getFullName() + ") có ngày nghỉ phép được duyệt vào ngày " + requesterSchedule.getWorkDate());
        }

        // Check compensation day conflicts for both staff after swap
        compensationDayRepository.findByStaffIdAndCompensationDate(targetSchedule.getStaff().getId(), targetSchedule.getWorkDate())
                .ifPresent(cd -> {
                    throw new BadRequestException("Nhân sự được đổi (" + targetSchedule.getStaff().getFullName() + ") có ngày nghỉ bù vào ngày " + targetSchedule.getWorkDate());
                });

        compensationDayRepository.findByStaffIdAndCompensationDate(requesterSchedule.getStaff().getId(), requesterSchedule.getWorkDate())
                .ifPresent(cd -> {
                    throw new BadRequestException("Nhân sự yêu cầu (" + requesterSchedule.getStaff().getFullName() + ") có ngày nghỉ bù vào ngày " + requesterSchedule.getWorkDate());
                });

        Staff tempStaff = requesterSchedule.getStaff();
        requesterSchedule.setStaff(targetSchedule.getStaff());
        targetSchedule.setStaff(tempStaff);

        scheduleRepository.save(requesterSchedule);
        scheduleRepository.save(targetSchedule);

        exchange.setStatus(ScheduleExchange.ExchangeStatus.APPROVED);
        exchange.setReviewedBy(reviewer);
        exchange.setReviewedAt(LocalDateTime.now());
        exchange.setReviewNote(reviewNote);

        ScheduleExchange saved = exchangeRepository.save(exchange);
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
        return ScheduleExchangeResponse.fromEntity(saved);
    }
}
