package com.hospital.scheduler.governance.controller;

import com.hospital.scheduler.governance.dto.AuditEventDTO;
import com.hospital.scheduler.governance.dto.ConfigVersionDTO;
import com.hospital.scheduler.governance.entity.ApprovalRequest;
import com.hospital.scheduler.governance.entity.GovernancePolicy;
import com.hospital.scheduler.governance.service.ApprovalWorkflowService;
import com.hospital.scheduler.governance.service.GovernanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for governance operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>Config Version: /governance/config/*</li>
 *   <li>Audit: /governance/audit/*</li>
 *   <li>Approval: /governance/approval/*</li>
 *   <li>Policy: /governance/policy/*</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/governance")
@RequiredArgsConstructor
@Slf4j
public class GovernanceController {

    private final GovernanceService governanceService;
    private final ApprovalWorkflowService approvalService;

    // ═══════════════════════════════════════════════════════════════════════
    // CONFIG VERSION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Create a new config version.
     */
    @PostMapping("/config/versions")
    public ResponseEntity<ConfigVersionDTO.Response> createVersion(
            @RequestBody ConfigVersionDTO.CreateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Integer userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName
    ) {
        return ResponseEntity.ok(governanceService.createVersion(request, userId, userName));
    }

    /**
     * Get version history for a period.
     */
    @GetMapping("/config/versions/period/{periodId}")
    public ResponseEntity<ConfigVersionDTO.HistoryResponse> getVersionHistory(@PathVariable Integer periodId) {
        return ResponseEntity.ok(governanceService.getVersionHistory(periodId));
    }

    /**
     * Get diff between two versions.
     */
    @GetMapping("/config/versions/diff")
    public ResponseEntity<ConfigVersionDTO.DiffResponse> getDiff(
            @RequestParam Integer fromVersionId,
            @RequestParam Integer toVersionId
    ) {
        return ResponseEntity.ok(governanceService.getDiff(fromVersionId, toVersionId));
    }

    /**
     * Rollback to a version.
     */
    @PostMapping("/config/versions/rollback")
    public ResponseEntity<ConfigVersionDTO.Response> rollback(
            @RequestBody ConfigVersionDTO.RollbackRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Integer userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName
    ) {
        return ResponseEntity.ok(governanceService.rollback(request, userId, userName));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AUDIT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Search audit events.
     */
    @GetMapping("/audit")
    public ResponseEntity<AuditEventDTO.SearchResponse> searchAudit(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        AuditEventDTO.SearchRequest request = AuditEventDTO.SearchRequest.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action != null ? com.hospital.scheduler.governance.entity.AuditEvent.AuditAction.valueOf(action) : null)
                .userId(userId)
                .page(page != null ? page : 0)
                .size(size != null ? size : 20)
                .build();

        return ResponseEntity.ok(governanceService.searchAudit(request));
    }

    /**
     * Get audit summary.
     */
    @GetMapping("/audit/summary")
    public ResponseEntity<AuditEventDTO.Summary> getAuditSummary() {
        return ResponseEntity.ok(governanceService.getAuditSummary());
    }

    /**
     * Get timeline for an entity.
     */
    @GetMapping("/audit/timeline/{entityType}/{entityId}")
    public ResponseEntity<List<AuditEventDTO.TimelineEvent>> getTimeline(
            @PathVariable String entityType,
            @PathVariable String entityId
    ) {
        return ResponseEntity.ok(governanceService.getTimeline(entityType, entityId));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // APPROVAL
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Create approval request.
     */
    @PostMapping("/approval")
    public ResponseEntity<ApprovalRequest> createApproval(
            @RequestBody ApprovalRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Integer userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName
    ) {
        return ResponseEntity.ok(approvalService.createRequest(request, userId, userName));
    }

    /**
     * Submit for approval.
     */
    @PostMapping("/approval/{id}/submit")
    public ResponseEntity<ApprovalRequest> submitApproval(
            @PathVariable Integer id,
            @RequestHeader(value = "X-User-Id", required = false) Integer userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName
    ) {
        return ResponseEntity.ok(approvalService.submit(id, userId, userName));
    }

    /**
     * Approve request.
     */
    @PostMapping("/approval/{id}/approve")
    public ResponseEntity<ApprovalRequest> approve(
            @PathVariable Integer id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) Integer userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName
    ) {
        return ResponseEntity.ok(approvalService.approve(id, userId, userName, body.get("comment")));
    }

    /**
     * Reject request.
     */
    @PostMapping("/approval/{id}/reject")
    public ResponseEntity<ApprovalRequest> reject(
            @PathVariable Integer id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) Integer userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName
    ) {
        return ResponseEntity.ok(approvalService.reject(id, userId, userName, body.get("reason")));
    }

    /**
     * Get pending approvals.
     */
    @GetMapping("/approval/pending")
    public ResponseEntity<List<ApprovalRequest>> getPending() {
        return ResponseEntity.ok(approvalService.getPendingRequests());
    }

    /**
     * Count pending.
     */
    @GetMapping("/approval/pending/count")
    public ResponseEntity<Map<String, Long>> countPending() {
        return ResponseEntity.ok(Map.of("count", approvalService.countPending()));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POLICY
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Evaluate policies for a context.
     */
    @PostMapping("/policy/evaluate")
    public ResponseEntity<List<GovernanceService.PolicyEvaluationResult>> evaluatePolicies(
            @RequestBody GovernanceService.PolicyContext context
    ) {
        return ResponseEntity.ok(governanceService.evaluatePolicies(context));
    }
}
