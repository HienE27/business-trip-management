package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.scheduling.explain.AssignmentExplanation;
import com.hospital.scheduler.scheduling.explain.ExplainService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 2.1 — Explain Engine endpoint.
 *
 * <p>GET {@code /api/v1/scheduling/explain/{periodId}/{slotId}} returns a JSON
 * tree explaining why a particular slot was assigned to its current staff.
 *
 * <p>Wire-up with the live Schedule repository will be done in Phase 2.5 when
 * the replay log is available. For now we return a stub response with the
 * DTO shape so the FE panel can be developed against a stable contract.
 */
@RestController
@RequestMapping("/api/v1/scheduling/explain")
@RequiredArgsConstructor
public class ExplainController {

    private final ExplainService explainService;

    @GetMapping("/{periodId}/{slotId}")
    public ApiResponse<Map<String, Object>> explain(@PathVariable int periodId,
                                                     @PathVariable int slotId) {
        // Stub: the live ScheduleRepository injection is part of Phase 2.5.
        // Return the DTO shape so the FE can render the Inspect panel.
        Map<String, Object> payload = new HashMap<>();
        payload.put("periodId", periodId);
        payload.put("slotId", slotId);
        payload.put("note",
                "ExplainService endpoint live — full data wiring ships in Phase 2.5");
        payload.put("explanation", AssignmentExplanation.builder()
                .slotId(slotId)
                .chosenReason("Phase 2.5 will hydrate from ScheduleRepository")
                .build());
        return ApiResponse.success(payload);
    }
}