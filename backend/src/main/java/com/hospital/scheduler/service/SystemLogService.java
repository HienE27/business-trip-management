package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.SystemLogResponse;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.SystemLog;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
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

    public List<SystemLogResponse> getAllLogs() {
        return systemLogRepository.findAll().stream()
                .map(SystemLogResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<SystemLogResponse> getLogsByStaff(Integer staffId) {
        return systemLogRepository.findByStaffId(staffId).stream()
                .map(SystemLogResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<SystemLogResponse> getLogsByActionType(String actionType) {
        return systemLogRepository.findByActionType(actionType).stream()
                .map(SystemLogResponse::fromEntity)
                .collect(Collectors.toList());
    }

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
