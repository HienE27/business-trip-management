package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateApplyWithEditsRequest {

    private Integer templateId;

    private Integer periodId;

    private List<TemplateEditItem> edits;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TemplateEditItem {
        private Integer slotId;
        private Integer assignedStaffId;
    }
}
