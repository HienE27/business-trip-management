package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.CompensationDay;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.CompensationDayRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompensationDayService Tests - Lấy danh sách ngày nghỉ bù theo period")
class CompensationDayServiceTest {

    @Mock
    private CompensationDayRepository compensationDayRepository;

    @InjectMocks
    private CompensationDayService service;

    private CompensationDay buildCompensationDay(Integer id, Integer staffId, String staffName,
                                                 LocalDate shiftDate, LocalDate compDate) {
        Staff staff = Staff.builder()
                .id(staffId)
                .fullName(staffName)
                .isActive(true)
                .build();
        staff.setStaffRoles(new java.util.HashSet<>());
        return CompensationDay.builder()
                .id(id)
                .staff(staff)
                .shiftDate(shiftDate)
                .compensationDate(compDate)
                .build();
    }

    @Test
    @DisplayName("getCompensationDaysByPeriod -> trả về list DTO đúng field")
    void getCompensationDaysByPeriod_mapsAllFields() {
        CompensationDay cd1 = buildCompensationDay(1, 10, "Nguyễn Văn A",
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 7));
        CompensationDay cd2 = buildCompensationDay(2, 11, "Trần Thị B",
                LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 8));

        when(compensationDayRepository.findByPeriodId(100))
                .thenReturn(List.of(cd1, cd2));

        List<CompensationDayService.CompensationDayDTO> result =
                service.getCompensationDaysByPeriod(100);

        assertThat(result).hasSize(2);

        CompensationDayService.CompensationDayDTO dto1 = result.get(0);
        assertThat(dto1.id()).isEqualTo(1);
        assertThat(dto1.staffId()).isEqualTo(10);
        assertThat(dto1.staffName()).isEqualTo("Nguyễn Văn A");
        assertThat(dto1.shiftDate()).isEqualTo("2026-07-06");
        assertThat(dto1.compensationDate()).isEqualTo("2026-07-07");

        CompensationDayService.CompensationDayDTO dto2 = result.get(1);
        assertThat(dto2.staffName()).isEqualTo("Trần Thị B");
        assertThat(dto2.compensationDate()).isEqualTo("2026-07-08");
    }

    @Test
    @DisplayName("getCompensationDaysByPeriod với period không có record -> trả về list rỗng")
    void getCompensationDaysByPeriod_empty() {
        when(compensationDayRepository.findByPeriodId(999))
                .thenReturn(List.of());

        List<CompensationDayService.CompensationDayDTO> result =
                service.getCompensationDaysByPeriod(999);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCompensationDaysByPeriod -> format date theo ISO yyyy-MM-dd")
    void getCompensationDaysByPeriod_dateFormat() {
        CompensationDay cd = buildCompensationDay(1, 1, "Test",
                LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1));

        when(compensationDayRepository.findByPeriodId(50))
                .thenReturn(List.of(cd));

        List<CompensationDayService.CompensationDayDTO> result =
                service.getCompensationDaysByPeriod(50);

        assertThat(result).hasSize(1);
        CompensationDayService.CompensationDayDTO dto = result.get(0);
        assertThat(dto.shiftDate()).isEqualTo("2026-12-31");
        assertThat(dto.compensationDate()).isEqualTo("2027-01-01");
    }
}