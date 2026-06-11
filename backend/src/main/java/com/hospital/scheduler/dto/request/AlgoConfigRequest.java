package com.hospital.scheduler.dto.request;

import com.hospital.scheduler.entity.AlgorithmConfig.ValueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlgoConfigRequest {

    @NotBlank(message = "Param key không được để trống")
    @Size(max = 50, message = "Param key tối đa 50 ký tự")
    private String paramKey;

    @NotBlank(message = "Param value không được để trống")
    @Size(max = 500, message = "Param value tối đa 500 ký tự")
    private String paramValue;

    @NotNull(message = "Value type không được để trống")
    private ValueType valueType;

    @Size(max = 255, message = "Description tối đa 255 ký tự")
    private String description;
}
