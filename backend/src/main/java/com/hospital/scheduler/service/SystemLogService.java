package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.SystemLogResponse;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.SystemLog;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemLogService {

    private final SystemLogRepository systemLogRepository;
    private final StaffRepository staffRepository;

    /**
     * BUGFIX (was BE#18) helper: produce a sorted PageRequest (newest first)
     * for the unbounded read endpoints.
     */
    private PageRequest sortedByNewest(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /**
     * BUGFIX (was BE#18): paginated variant of {@code getAllLogs}. system_log
     * grows monotonically; without pagination the previous variant could
     * dump millions of rows and OOM the JSON serializer.
     */
    public Page<SystemLogResponse> getAllLogsPage(int page, int size) {
        return systemLogRepository.findAll(sortedByNewest(page, size))
                .map(SystemLogResponse::fromEntity);
    }

    /**
     * Deprecated: unbounded listing used to dump millions of rows. Use
     * {@link #getAllLogsPage(int, int)} instead. Kept only for callers still
     * relying on the legacy payload shape.
     */
    @Deprecated
    public List<SystemLogResponse> getAllLogs() {
        return systemLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(SystemLogResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * BUGFIX (was BE#18): paginated variant of getLogsByStaff.
     */
    public Page<SystemLogResponse> getLogsByStaffPage(Integer staffId, int page, int size) {
        return systemLogRepository.findByStaffId(staffId, sortedByNewest(page, size))
                .map(SystemLogResponse::fromEntity);
    }

    @Deprecated
    public List<SystemLogResponse> getLogsByStaff(Integer staffId) {
        return systemLogRepository.findByStaffId(staffId).stream()
                .map(SystemLogResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * BUGFIX (was BE#18): paginated variant of getLogsByActionType.
     */
    public Page<SystemLogResponse> getLogsByActionTypePage(String actionType, int page, int size) {
        return systemLogRepository.findByActionType(actionType, sortedByNewest(page, size))
                .map(SystemLogResponse::fromEntity);
    }

    @Deprecated
    public List<SystemLogResponse> getLogsByActionType(String actionType) {
        return systemLogRepository.findByActionType(actionType).stream()
                .map(SystemLogResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * BUGFIX (was BE#18): paginated variant of getLogsByDateRange.
     */
    public Page<SystemLogResponse> getLogsByDateRangePage(LocalDateTime startDate, LocalDateTime endDate, int page, int size) {
        return systemLogRepository.findByDateRange(startDate, endDate, sortedByNewest(page, size))
                .map(SystemLogResponse::fromEntity);
    }

    @Deprecated
    public List<SystemLogResponse> getLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return systemLogRepository.findByDateRange(startDate, endDate).stream()
                .map(SystemLogResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public SystemLog logSystem(String actionType, String description, Integer staffId, String ipAddress, String userAgent) {
        Staff staff = null;
        if (staffId != null) {
            staff = staffRepository.findById(staffId).orElse(null);
        }

        SystemLog systemLog = SystemLog.builder()
                .staff(staff)
                .actionType(actionType)
                .description(description)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        return systemLogRepository.save(systemLog);
    }
}
