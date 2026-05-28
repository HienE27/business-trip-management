package com.hospital.scheduler.dto.response;

import com.hospital.scheduler.entity.ShiftRequirement;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftRequirementResponse {

    private Integer id;
    private Integer periodId;
    private LocalDate workDate;
    private ShiftTypeSummary shiftType;
    private SpecialtySummary specialty;
    private Integer requiredStaffCount;
    private Integer assignedStaffCount;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShiftTypeSummary {
        private String id;
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SpecialtySummary {
        private Integer id;
        private String name;
    }

    public static ShiftRequirementResponse fromEntity(ShiftRequirement entity) {
        return ShiftRequirementResponse.builder()
                .id(entity.getId())
                .periodId(entity.getPeriod().getId())
                .workDate(entity.getWorkDate())
                .shiftType(entity.getShiftType() != null ? ShiftTypeSummary.builder()
                        .id(entity.getShiftType().getId())
                        .name(entity.getShiftType().getName())
                        .build() : null)
                .specialty(entity.getSpecialty() != null ? SpecialtySummary.builder()
                        .id(entity.getSpecialty().getId())
                        .name(entity.getSpecialty().getName())
                        .build() : null)
                .requiredStaffCount(entity.getRequiredStaffCount())
                .note(entity.getNote())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
