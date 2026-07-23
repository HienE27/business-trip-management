package com.hospital.scheduler.digital.sandbox.controller;

import com.hospital.scheduler.digital.sandbox.domain.SandboxStatus;
import com.hospital.scheduler.digital.sandbox.domain.SimulationMode;
import com.hospital.scheduler.digital.sandbox.entity.SandboxSession;
import com.hospital.scheduler.digital.sandbox.entity.SandboxSnapshot;
import com.hospital.scheduler.digital.sandbox.repository.SandboxSnapshotRepository;
import com.hospital.scheduler.digital.sandbox.service.*;
import com.hospital.scheduler.security.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for Sandbox Digital Twin operations.
 *
 * <p>API Endpoints:
 * <pre>
 * POST   /sandbox                    Create sandbox
 * GET    /sandbox                    List sandboxes
 * GET    /sandbox/{id}               Get sandbox
 * DELETE /sandbox/{id}               Delete sandbox
 *
 * POST   /sandbox/{id}/run           Start simulation
 * POST   /sandbox/{id}/pause        Pause simulation
 * POST   /sandbox/{id}/resume       Resume simulation
 * POST   /sandbox/{id}/cancel       Cancel simulation
 *
 * GET    /sandbox/{id}/snapshots    List snapshots
 * GET    /sandbox/{id}/timeline     Get timeline
 * GET    /sandbox/{id}/metrics      Get metrics
 *
 * POST   /sandbox/{id}/promote      Promote to production
 * GET    /sandbox/{id}/diff         Get diff preview
 * GET    /sandbox/{id}/validate     Validate before promote
 *
 * POST   /sandbox/{id}/pin          Toggle pin
 * POST   /sandbox/{id}/extend       Extend TTL
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/sandbox")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
public class SandboxController {

    private final SandboxService sandboxService;
    private final SandboxPromotionService promotionService;
    private final SandboxCleanupService cleanupService;
    private final SandboxSnapshotRepository snapshotRepository;

    // ─── Session CRUD ──────────────────────────────────────────────────────

    /**
     * Create a new sandbox by cloning a period.
     */
    @PostMapping
    public ResponseEntity<SandboxSessionResponse> createSandbox(
            @RequestBody CreateSandboxRequest request,
            @AuthUser String username
    ) {
        log.info("Creating sandbox for period {} by user {}", request.periodId(), username);

        SandboxSession session = sandboxService.createSandbox(
                request.periodId(),
                request.profileId(),
                username,
                request.name(),
                request.simulationMode(),
                request.ttlHours()
        );

        return ResponseEntity.ok(toResponse(session));
    }

    /**
     * List all sandboxes for current user.
     */
    @GetMapping
    public ResponseEntity<List<SandboxSessionResponse>> listSandboxes(
            @RequestParam(required = false) SandboxStatus status,
            @RequestParam(required = false) Integer periodId,
            @AuthUser String username
    ) {
        List<SandboxSession> sessions;

        if (status != null) {
            sessions = sandboxService.getByStatus(status);
        } else if (periodId != null) {
            sessions = sandboxService.getByPeriod(periodId);
        } else {
            sessions = sandboxService.getByUser(username);
        }

        return ResponseEntity.ok(sessions.stream().map(this::toResponse).toList());
    }

    /**
     * Get sandbox by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<SandboxSessionResponse> getSandbox(@PathVariable Long id) {
        return sandboxService.getById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get sandbox by session key.
     */
    @GetMapping("/key/{sessionKey}")
    public ResponseEntity<SandboxSessionResponse> getSandboxByKey(@PathVariable String sessionKey) {
        return sandboxService.getByKey(sessionKey)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete sandbox.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSandbox(@PathVariable Long id) {
        sandboxService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Simulation Control ──────────────────────────────────────────────

    /**
     * Start simulation.
     */
    @PostMapping("/{sessionKey}/run")
    public ResponseEntity<SandboxSessionResponse> startSimulation(@PathVariable String sessionKey) {
        SandboxSession session = sandboxService.start(sessionKey);
        return ResponseEntity.ok(toResponse(session));
    }

    /**
     * Pause simulation.
     */
    @PostMapping("/{sessionKey}/pause")
    public ResponseEntity<SandboxSessionResponse> pauseSimulation(@PathVariable String sessionKey) {
        SandboxSession session = sandboxService.pause(sessionKey);
        return ResponseEntity.ok(toResponse(session));
    }

    /**
     * Resume simulation.
     */
    @PostMapping("/{sessionKey}/resume")
    public ResponseEntity<SandboxSessionResponse> resumeSimulation(@PathVariable String sessionKey) {
        SandboxSession session = sandboxService.resume(sessionKey);
        return ResponseEntity.ok(toResponse(session));
    }

    /**
     * Cancel simulation.
     */
    @PostMapping("/{sessionKey}/cancel")
    public ResponseEntity<SandboxSessionResponse> cancelSimulation(@PathVariable String sessionKey) {
        SandboxSession session = sandboxService.cancel(sessionKey);
        return ResponseEntity.ok(toResponse(session));
    }

    // ─── Snapshots & Timeline ────────────────────────────────────────────

    /**
     * List snapshots for a session.
     */
    @GetMapping("/{sessionKey}/snapshots")
    public ResponseEntity<List<SnapshotResponse>> listSnapshots(
            @PathVariable String sessionKey,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return sandboxService.getByKey(sessionKey)
                .map(session -> {
                    List<SandboxSnapshot> snapshots = snapshotRepository
                            .findBySessionOrderByIterationAsc(session)
                            .stream()
                            .limit(limit)
                            .toList();
                    return ResponseEntity.ok(snapshots.stream().map(this::toSnapshotResponse).toList());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get snapshot by iteration.
     */
    @GetMapping("/{sessionKey}/snapshots/{iteration}")
    public ResponseEntity<SnapshotResponse> getSnapshot(
            @PathVariable String sessionKey,
            @PathVariable Integer iteration
    ) {
        return sandboxService.getByKey(sessionKey)
                .flatMap(session -> snapshotRepository.findBySessionAndIteration(session, iteration))
                .map(s -> ResponseEntity.ok(toSnapshotResponse(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get timeline data for visualization.
     */
    @GetMapping("/{sessionKey}/timeline")
    public ResponseEntity<TimelineResponse> getTimeline(@PathVariable String sessionKey) {
        return sandboxService.getByKey(sessionKey)
                .map(session -> {
                    List<SandboxSnapshot> snapshots = snapshotRepository
                            .findBySessionOrderByIterationAsc(session);

                    TimelineResponse response = new TimelineResponse(
                            sessionKey,
                            session.getIterations(),
                            snapshots.stream().map(s -> new TimelineResponse.IterationPoint(
                                    s.getIteration(),
                                    s.getScore(),
                                    s.getCoverageRate(),
                                    s.getFairnessCv(),
                                    s.getViolations(),
                                    s.getAccepted(),
                                    s.getMoveType(),
                                    s.getScoreDelta()
                            )).toList()
                    );

                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get current metrics for a session.
     */
    @GetMapping("/{sessionKey}/metrics")
    public ResponseEntity<MetricsResponse> getMetrics(@PathVariable String sessionKey) {
        return sandboxService.getByKey(sessionKey)
                .map(session -> {
                    MetricsResponse response = new MetricsResponse(
                            sessionKey,
                            session.getStatus(),
                            session.getIterations(),
                            session.getBestScore(),
                            session.getCoverageRate(),
                            session.getFairnessCv(),
                            session.getViolations(),
                            session.getRuntimeSeconds()
                    );
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── Promotion ──────────────────────────────────────────────────────

    /**
     * Get diff between sandbox and production.
     */
    @GetMapping("/{sessionKey}/diff")
    public ResponseEntity<?> getDiff(@PathVariable String sessionKey) {
        try {
            SandboxPromotionService.PromotionDiff diff = promotionService.generateDiff(sessionKey);
            return ResponseEntity.ok(diff);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Validate promotion.
     */
    @GetMapping("/{sessionKey}/validate")
    public ResponseEntity<?> validatePromotion(@PathVariable String sessionKey) {
        try {
            SandboxPromotionService.PromotionValidation validation = promotionService.validate(sessionKey);
            return ResponseEntity.ok(validation);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Promote sandbox to production.
     */
    @PostMapping("/{sessionKey}/promote")
    public ResponseEntity<?> promote(@PathVariable String sessionKey, @AuthUser String username) {
        try {
            SandboxPromotionService.PromotionResult result = promotionService.promote(sessionKey, username);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Session Management ─────────────────────────────────────────────

    /**
     * Toggle pin status.
     */
    @PostMapping("/{sessionKey}/pin")
    public ResponseEntity<SandboxSessionResponse> togglePin(@PathVariable String sessionKey) {
        SandboxSession session = sandboxService.togglePin(sessionKey);
        return ResponseEntity.ok(toResponse(session));
    }

    /**
     * Extend TTL.
     */
    @PostMapping("/{sessionKey}/extend")
    public ResponseEntity<SandboxSessionResponse> extendTtl(
            @PathVariable String sessionKey,
            @RequestParam(defaultValue = "24") int hours
    ) {
        SandboxSession session = sandboxService.extendTtl(sessionKey, hours);
        return ResponseEntity.ok(toResponse(session));
    }

    // ─── Admin Operations ──────────────────────────────────────────────

    /**
     * Get cleanup stats (admin only).
     */
    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SandboxCleanupService.CleanupStats> getCleanupStats() {
        return ResponseEntity.ok(cleanupService.getCleanupStats());
    }

    /**
     * Force cleanup expired sessions (admin only).
     */
    @PostMapping("/admin/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> forceCleanup() {
        int expired = cleanupService.expireSessions();
        int cleaned = cleanupService.cleanupExpiredSessions();
        return ResponseEntity.ok(Map.of("expired", expired, "cleaned", cleaned));
    }

    // ─── DTOs ──────────────────────────────────────────────────────────

    public record CreateSandboxRequest(
            Integer periodId,
            Long profileId,
            String name,
            SimulationMode simulationMode,
            Integer ttlHours
    ) {}

    public record SandboxSessionResponse(
            Long id,
            String sessionKey,
            String name,
            SandboxStatus status,
            SimulationMode simulationMode,
            String createdBy,
            String createdAt,
            String expiresAt,
            Integer sourcePeriodId,
            Long profileId,
            Integer iterations,
            Double bestScore,
            Double coverageRate,
            Double fairnessCv,
            Integer violations,
            Integer runtimeSeconds,
            Boolean isPinned,
            String description
    ) {}

    public record SnapshotResponse(
            Long id,
            Integer iteration,
            Double score,
            Double coverageRate,
            Double fairnessCv,
            Integer violations,
            String moveType,
            Integer staffId,
            Integer slotId,
            Integer targetStaffId,
            Double scoreDelta,
            Boolean accepted,
            Double acceptanceProbability,
            Double temperature,
            Integer tabuRemaining,
            String createdAt,
            Boolean isCheckpoint
    ) {}

    public record TimelineResponse(
            String sessionKey,
            Integer totalIterations,
            List<IterationPoint> iterations
    ) {
        public record IterationPoint(
                Integer iteration,
                Double score,
                Double coverageRate,
                Double fairnessCv,
                Integer violations,
                Boolean accepted,
                String moveType,
                Double scoreDelta
        ) {}
    }

    public record MetricsResponse(
            String sessionKey,
            SandboxStatus status,
            Integer iterations,
            Double bestScore,
            Double coverageRate,
            Double fairnessCv,
            Integer violations,
            Integer runtimeSeconds
    ) {}

    // ─── Mapping ────────────────────────────────────────────────────────

    private SandboxSessionResponse toResponse(SandboxSession session) {
        return new SandboxSessionResponse(
                session.getId(),
                session.getSessionKey(),
                session.getName(),
                session.getStatus(),
                session.getSimulationMode(),
                session.getCreatedBy(),
                session.getCreatedAt() != null ? session.getCreatedAt().toString() : null,
                session.getExpiresAt() != null ? session.getExpiresAt().toString() : null,
                session.getSourcePeriodId(),
                session.getProfileId(),
                session.getIterations(),
                session.getBestScore(),
                session.getCoverageRate(),
                session.getFairnessCv(),
                session.getViolations(),
                session.getRuntimeSeconds(),
                session.getIsPinned(),
                session.getDescription()
        );
    }

    private SnapshotResponse toSnapshotResponse(SandboxSnapshot snapshot) {
        return new SnapshotResponse(
                snapshot.getId(),
                snapshot.getIteration(),
                snapshot.getScore(),
                snapshot.getCoverageRate(),
                snapshot.getFairnessCv(),
                snapshot.getViolations(),
                snapshot.getMoveType(),
                snapshot.getStaffId(),
                snapshot.getSlotId(),
                snapshot.getTargetStaffId(),
                snapshot.getScoreDelta(),
                snapshot.getAccepted(),
                snapshot.getAcceptanceProbability(),
                snapshot.getTemperature(),
                snapshot.getTabuRemaining(),
                snapshot.getCreatedAt() != null ? snapshot.getCreatedAt().toString() : null,
                snapshot.getIsCheckpoint()
        );
    }
}
