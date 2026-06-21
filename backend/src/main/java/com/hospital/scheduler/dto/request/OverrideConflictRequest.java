package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class OverrideConflictRequest {

    @NotBlank(message = "Lý do override không được để trống")
    @Size(max = 500, message = "Lý do override không quá 500 ký tự")
    private String reason;
}
