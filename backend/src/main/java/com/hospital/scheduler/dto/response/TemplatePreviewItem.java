package com.hospital.scheduler.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplatePreviewItem {

    private Integer id; // requirement slot id (0 for new)
    private String workDate;
    private String dayOfWeek;
    private String shiftTypeId;
    private String shiftTypeName;
    private String specialtyName;
    private Integer requiredStaffCount;
    private Integer assignedStaffId;
    private String assignedStaffName;
}
