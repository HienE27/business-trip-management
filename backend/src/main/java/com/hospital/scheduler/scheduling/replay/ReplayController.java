package com.hospital.scheduler.scheduling.replay;

import com.hospital.scheduler.dto.ApiResponse;
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
 * Replay endpoint — returns the move log for a run, ready to be stepped
 * through by the FE replay page.
 */
@RestController
@RequestMapping("/api/v1/scheduling/replay")
@RequiredArgsConstructor
public class ReplayController {

    private final MoveLogRegistry registry;

    @GetMapping("/{runId}")
    public ApiResponse<Map<String, Object>> replay(@PathVariable String runId) {
        MoveLog log = registry.get(runId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", runId);
        if (log == null) {
            payload.put("entries", List.of());
            payload.put("size", 0);
            return ApiResponse.success(payload);
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (MoveLog.MoveRecord r : log.snapshot()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("iteration", r.getIteration());
            body.put("elapsed", r.getElapsedMillis());
            body.put("moveType", r.getMoveType());
            body.put("slotId", r.getSlotId());
            body.put("previousStaffId", r.getPreviousStaffId());
            body.put("newStaffId", r.getNewStaffId());
            body.put("hardDelta", r.getHardDelta());
            body.put("coverageDelta", r.getCoverageDelta());
            body.put("accepted", r.isAccepted());
            body.put("score", scoreMap(r));
            entries.add(body);
        }
        payload.put("entries", entries);
        payload.put("size", log.size());
        return ApiResponse.success(payload);
    }

    @GetMapping("/runs")
    public ApiResponse<List<String>> runs() {
        return ApiResponse.success(registry.runIds());
    }

    private Map<String, Object> scoreMap(MoveLog.MoveRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (r.getScoreSnapshot() == null) return m;
        m.put("hard", r.getScoreSnapshot().getHardViolations());
        m.put("coverage", r.getScoreSnapshot().getCoverage());
        m.put("gap", r.getScoreSnapshot().getGap());
        m.put("gini", r.getScoreSnapshot().getGini());
        return m;
    }
}
