package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffRequest {

    private String username;

    private String staffCode;

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 100, message = "Họ tên không quá 100 ký tự")
    private String fullName;

    @Size(min = 6, message = "Password phải có ít nhất 6 ký tự")
    private String password;

    @Pattern(regexp = "^$|^[0-9]{10,20}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    @Email(message = "Email không hợp lệ")
    private String email;

    private String position;
    private Integer specialtyId;
    @Builder.Default
    private Integer maxShiftsPerMonth = 5;
    private java.util.List<String> roles;
    private String status;

    private Integer id;
    private String specialtyName;

    @PastOrPresent(message = "Ngày vào làm không được là ngày trong tương lai")
    private LocalDateTime hireDate;
}
