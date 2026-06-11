package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveTemplateRequest {

    @NotNull(message = "ID kỳ lịch không được để trống")
    private Integer periodId;

    @NotBlank(message = "Tên mẫu lịch không được để trống")
    private String templateName;

    private String description;

    private String algorithmType;

    private Map<String, Object> algorithmConfig;

    private List<Integer> scheduleIds;
}
