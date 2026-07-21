package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.scheduling.telemetry.HeatmapBuilder;
import com.hospital.scheduler.scheduling.telemetry.HeatmapService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Heatmap endpoint — {@code GET /api/v1/scheduling/heatmap/{periodId}}.
 *
 * <p>Returns a serializable view of {@link HeatmapBuilder.Heatmap} suitable
 * for direct rendering by the FE widget.
 */
@RestController
@RequestMapping("/api/v1/scheduling/heatmap")
@RequiredArgsConstructor
public class HeatmapController {

    private final HeatmapService heatmapService;

    @GetMapping("/{periodId}")
    public ApiResponse<Map<String, Object>> heatmap(
            @PathVariable Integer periodId,
            @RequestParam(value = "metric", defaultValue = "load") String metric) {
        HeatmapBuilder.Metric m = parseMetric(metric);
        HeatmapBuilder.Heatmap heat = heatmapService.buildForPeriod(periodId, m);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("periodId", periodId);
        body.put("metric", heat.metric().name());
        body.put("periodDays", heat.periodDays());
        body.put("startDate", heat.startDate() != null ? heat.startDate().toString() : null);
        body.put("endDate", heat.endDate() != null ? heat.endDate().toString() : null);
        body.put("maxRaw", heat.maxRaw());

        var rows = new java.util.ArrayList<Map<String, Object>>();
        for (var row : heat.rows()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("staffId", row.staffId());
            r.put("displayName", row.displayName());
            r.put("rawTotal", row.rawTotal());
            r.put("intensities", row.intensities());
            rows.add(r);
        }
        body.put("rows", rows);

        return ApiResponse.success(body);
    }

    private HeatmapBuilder.Metric parseMetric(String metric) {
        if (metric == null) return HeatmapBuilder.Metric.LOAD;
        return switch (metric.toLowerCase()) {
            case "weekend" -> HeatmapBuilder.Metric.WEEKEND;
            case "consecutive" -> HeatmapBuilder.Metric.CONSECUTIVE;
            default -> HeatmapBuilder.Metric.LOAD;
        };
    }
}
