package com.hospital.scheduler.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkL01Request {

    @NotNull(message = "ID kỳ lịch không được để trống")
    private Integer periodId;

    @Valid
    @NotEmpty(message = "Danh sách entries không được để trống")
    private List<L01Entry> entries;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class L01Entry {
        @NotNull(message = "Ngày làm việc không được để trống")
        private LocalDate workDate;

        @NotNull(message = "ID nhân sự không được để trống")
        private Integer staffId;
    }
}
