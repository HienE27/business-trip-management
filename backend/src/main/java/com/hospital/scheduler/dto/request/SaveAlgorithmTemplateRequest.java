package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveAlgorithmTemplateRequest {

    @NotBlank(message = "Tên mẫu cấu hình không được để trống")
    private String name;

    private String description;

    @NotBlank(message = "Loại thuật toán không được để trống")
    private String algorithmType;

    private Map<String, Object> params;
}
