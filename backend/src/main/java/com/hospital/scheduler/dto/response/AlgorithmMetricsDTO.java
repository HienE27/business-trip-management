package com.hospital.scheduler.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlgorithmMetricsDTO {
    private Integer id;
    private String algorithmType;
    private Integer executionTimeMs;
    private BigDecimal coverageRate;
    private BigDecimal balanceScore;
    private Integer conflictCount;
    private LocalDateTime createdAt;
}
