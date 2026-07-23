package com.hospital.scheduler.whatif.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Scenario request DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioRequest {

    String name;
    String description;
    Integer sourcePeriodId;
    Map<String, Object> configOverrides;
    Integer parentScenarioId;
    String tags;
}
