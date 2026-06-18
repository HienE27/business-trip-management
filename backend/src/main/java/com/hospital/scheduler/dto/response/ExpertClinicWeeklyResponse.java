package com.hospital.scheduler.dto.response;

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
public class ExpertClinicWeeklyResponse {

    private Integer periodId;
    private String periodName;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private List<DaySchedule> weekSchedule;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DaySchedule {
        private LocalDate date;
        private String dayOfWeek;
        private int dayOfWeekIndex;
        private List<ScheduleResponse> schedules;
    }
}
