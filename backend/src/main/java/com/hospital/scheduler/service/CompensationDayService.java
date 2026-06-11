package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.CompensationDay;
import com.hospital.scheduler.repository.CompensationDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompensationDayService {

    private final CompensationDayRepository compensationDayRepository;

    public record CompensationDayDTO(
            Integer id,
            Integer staffId,
            String staffName,
            String shiftDate,
            String compensationDate
    ) {}

    public List<CompensationDayDTO> getCompensationDaysByPeriod(Integer periodId) {
        return compensationDayRepository.findByPeriodId(periodId).stream()
                .map(this::toDTO)
                .toList();
    }

    private CompensationDayDTO toDTO(CompensationDay cd) {
        return new CompensationDayDTO(
                cd.getId(),
                cd.getStaff().getId(),
                cd.getStaff().getFullName(),
                cd.getShiftDate().toString(),
                cd.getCompensationDate().toString()
        );
    }
}
