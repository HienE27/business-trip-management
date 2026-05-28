package com.hospital.scheduler.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffSearchRequest {

    private String keyword;
    private Integer specialtyId;
    private String status;
    private String role;
}
