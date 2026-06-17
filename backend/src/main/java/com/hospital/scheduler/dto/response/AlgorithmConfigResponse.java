package com.hospital.scheduler.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlgorithmConfigResponse {

    private String name;
    private String description;
    private String algorithmType;
    private Map<String, Object> params;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
