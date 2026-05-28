package com.hospital.scheduler.dto.response;

import com.hospital.scheduler.entity.ScheduleTemplate;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleTemplateResponse {

    private Integer id;
    private String name;
    private String description;
    private Integer dayOfWeek;
    private String shiftTypeId;
    private String shiftTypeName;
    private SpecialtyResponse specialty;
    private Integer requiredStaffCount;
    private Boolean isActive;
    private LocalDateTime createdAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SpecialtyResponse {
        private Integer id;
        private String name;
    }

    public static ScheduleTemplateResponse fromEntity(ScheduleTemplate template) {
        SpecialtyResponse specialtyResp = null;
        if (template.getSpecialty() != null) {
            specialtyResp = SpecialtyResponse.builder()
                    .id(template.getSpecialty().getId())
                    .name(template.getSpecialty().getName())
                    .build();
        }
        return ScheduleTemplateResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .dayOfWeek(template.getDayOfWeek())
                .shiftTypeId(template.getShiftTypeId())
                .requiredStaffCount(template.getRequiredStaffCount())
                .isActive(template.getIsActive())
                .createdAt(template.getCreatedAt())
                .specialty(specialtyResp)
                .build();
    }
}
