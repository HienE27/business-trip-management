package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.ConflictCheckResponse;
import com.hospital.scheduler.entity.ScheduleConflict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConflictBroadcastService {

    private static final String CONFLICT_TOPIC = "/topic/conflicts";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Broadcast a conflict event to all subscribers of /topic/conflicts.
     * The payload is a Map with conflict details suitable for frontend consumption.
     */
    public void broadcastConflict(ScheduleConflict conflict, ConflictCheckResponse.ConflictDetail detail) {
        Map<String, Object> payload = buildConflictPayload(conflict, detail);
        doSend(payload);
        log.info("Broadcasted conflict event: conflictId={}, scheduleId={}",
                conflict.getId(), conflict.getSchedule().getId());
    }

    /**
     * Broadcast a batch of conflicts (e.g., from a period conflict check).
     */
    public void broadcastConflictBatch(List<ConflictCheckResponse.ConflictDetail> conflicts, Integer periodId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "CONFLICT_BATCH");
        payload.put("periodId", periodId);
        payload.put("totalConflicts", conflicts.size());
        payload.put("conflicts", conflicts);
        payload.put("timestamp", LocalDateTime.now().toString());
        doSend(payload);
        log.info("Broadcasted conflict batch: periodId={}, totalConflicts={}", periodId, conflicts.size());
    }

    /**
     * Broadcast a conflict resolved event.
     */
    public void broadcastConflictResolved(Integer conflictId, Integer scheduleId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "CONFLICT_RESOLVED");
        payload.put("conflictId", conflictId);
        payload.put("scheduleId", scheduleId);
        payload.put("timestamp", LocalDateTime.now().toString());
        doSend(payload);
        log.info("Broadcasted conflict resolved: conflictId={}, scheduleId={}", conflictId, scheduleId);
    }

    private Map<String, Object> buildConflictPayload(ScheduleConflict conflict, ConflictCheckResponse.ConflictDetail detail) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "CONFLICT_DETECTED");
        payload.put("conflictId", conflict.getId());
        payload.put("scheduleId", conflict.getSchedule().getId());
        payload.put("conflictType", conflict.getConflictType().name());
        payload.put("description", conflict.getDescription());
        payload.put("isResolved", conflict.getIsResolved());
        payload.put("timestamp", LocalDateTime.now().toString());

        if (detail != null) {
            payload.put("staffName", detail.getStaffName());
            payload.put("workDate", detail.getWorkDate() != null ? detail.getWorkDate().toString() : null);
            payload.put("shiftTypeId", detail.getShiftTypeId());
            payload.put("shiftTypeName", detail.getShiftTypeName());
            payload.put("conflictReasons", detail.getConflictReasons());
        }
        return payload;
    }

    /**
     * Dispatches to SimpMessagingTemplate using the destination + headers variant
     * so the compiler can resolve overloads unambiguously.
     */
    private void doSend(Map<String, Object> payload) {
        messagingTemplate.convertAndSend(CONFLICT_TOPIC, payload, new java.util.HashMap<>());
    }
}
