package com.hospital.scheduler.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.entity.AlgorithmConstraintReport;
import com.hospital.scheduler.scheduling.telemetry.ConstraintReportService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Constraint report endpoint — returns all reports persisted for a period,
 * newest first.
 */
@RestController
@RequestMapping("/api/v1/scheduling/metrics")
@RequiredArgsConstructor
public class ConstraintReportController {

    private final ConstraintReportService service;
    private final ObjectMapper objectMapper;

    @GetMapping("/{periodId}/report")
    public ApiResponse<Map<String, Object>> report(@PathVariable Integer periodId) {
        List<AlgorithmConstraintReport> rows = service.findByPeriod(periodId);
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (var row : rows) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", row.getId());
            body.put("runId", row.getRunId());
            body.put("algorithmType", row.getAlgorithmType());
            body.put("createdAt", row.getCreatedAt() != null ? row.getCreatedAt().toString() : null);
            try {
                JsonNode parsed = objectMapper.readTree(row.getReportJson());
                body.put("report", parsed);
            } catch (Exception e) {
                body.put("report", null);
                body.put("parseError", e.getMessage());
            }
            items.add(body);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("periodId", periodId);
        payload.put("reports", items);
        return ApiResponse.success(payload);
    }
}
