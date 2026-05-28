package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.SchedulePeriodRequest;
import com.hospital.scheduler.dto.response.SchedulePeriodResponse;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import com.hospital.scheduler.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SchedulePeriodService {

    private final SchedulePeriodRepository periodRepository;
    private final StaffRepository staffRepository;

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

        period.setPeriodName(request.getPeriodName());
        period.setStartDate(request.getStartDate());
        period.setEndDate(request.getEndDate());

        return toResponse(periodRepository.save(period));
    }

    public SchedulePeriodResponse publishPeriod(Integer id) {
        SchedulePeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + id));

        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể công bố kỳ lịch ở trạng thái DRAFT");
        }

        period.setStatus(SchedulePeriod.PeriodStatus.PUBLISHED);
        period.setPublishedAt(LocalDateTime.now());
        return toResponse(periodRepository.save(period));
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
                .publishedAt(period.getPublishedAt())
                .createdAt(period.getCreatedAt())
                .updatedAt(period.getUpdatedAt())
                .build();
    }
}
