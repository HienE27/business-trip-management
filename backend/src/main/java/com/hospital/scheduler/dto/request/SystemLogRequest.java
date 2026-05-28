package com.hospital.scheduler.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemLogRequest {

    private Integer staffId;
    private String actionType;
    private String description;
    private String ipAddress;
    private String userAgent;
}
