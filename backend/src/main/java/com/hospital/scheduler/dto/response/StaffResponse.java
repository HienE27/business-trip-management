package com.hospital.scheduler.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffResponse {
    private Integer id;
    private String username;
    private String fullName;
    private String phone;
    private String email;
    private SpecialtyResponse specialty;
    private Integer maxShiftsPerMonth;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> roles;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SpecialtyResponse {
        private Integer id;
        private String name;
    }
}
