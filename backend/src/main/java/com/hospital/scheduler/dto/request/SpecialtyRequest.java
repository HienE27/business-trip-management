package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialtyRequest {

    @NotBlank(message = "Tên chuyên khoa không được để trống")
    @Size(max = 50, message = "Tên chuyên khoa không quá 50 ký tự")
    private String name;

    private String description;
}
