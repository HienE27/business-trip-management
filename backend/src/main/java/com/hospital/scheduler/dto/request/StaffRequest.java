package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffRequest {

    @NotBlank(message = "Username không được để trống")
    @Size(max = 50, message = "Username không quá 50 ký tự")
    private String username;

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 100, message = "Họ tên không quá 100 ký tự")
    private String fullName;

    @Size(min = 6, message = "Password phải có ít nhất 6 ký tự")
    private String password;

    @Pattern(regexp = "^$|^[0-9]{10,20}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    @Email(message = "Email không hợp lệ")
    private String email;

    private Integer specialtyId;
    @Builder.Default
    private Integer maxShiftsPerMonth = 5;
    private java.util.List<String> roles;
    private String status;

    private Integer id;
    private String specialtyName;
}
