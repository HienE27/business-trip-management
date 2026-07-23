package com.hospital.scheduler.governance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.governance.entity.ApprovalRequest;
import com.hospital.scheduler.governance.repository.ApprovalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for approval workflow management.
 *
 * <p>Supports multi-level approval:
 * <ul>
 *   <li>Draft → Submit → Review → Approved → Applied → Archived</li>
 *   <li>Multiple approval levels</li>
 *   <li>Comments and rejection reasons</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalWorkflowService {

    private final ApprovalRequestRepository approvalRepository;
    private final ObjectMapper objectMapper;

    /**
     * Create a new approval request.
     */
    @Transactional
    public ApprovalRequest createRequest(ApprovalRequest request, Integer submittedBy, String submittedByName) {
        request.setSubmittedBy(submittedBy);
        request.setSubmittedByName(submittedByName);
        request.setStatus(ApprovalRequest.ApprovalStatus.DRAFT);
        request.setApprovalLevel(1);
        request.setRequiredLevels(1);

        return approvalRepository.save(request);
    }

    /**
     * Submit a request for approval.
     */
    @Transactional
    public ApprovalRequest submit(Integer requestId, Integer submittedBy, String submittedByName) {
        ApprovalRequest request = approvalRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        if (request.getStatus() != ApprovalRequest.ApprovalStatus.DRAFT
                && request.getStatus() != ApprovalRequest.ApprovalStatus.CHANGES_REQUESTED) {
            throw new IllegalStateException("Can only submit draft or changes-requested requests");
        }

        request.setStatus(ApprovalRequest.ApprovalStatus.SUBMITTED);
        request.setSubmittedBy(submittedBy);
        request.setSubmittedByName(submittedByName);
        // BUGFIX: re-starting approval from level 1 after a CHANGES_REQUESTED cycle.
        // Previously the level was retained, so a 3-level request whose creator
        // answered change requests at level 3 would skip levels 1–2 on resubmit.
        request.setApprovalLevel(1);

        return approvalRepository.save(request);
    }

    /**
     * Approve a request.
     */
    @Transactional
    public ApprovalRequest approve(Integer requestId, Integer reviewedBy, String reviewedByName, String comment) {
        ApprovalRequest request = approvalRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        if (request.getStatus() == ApprovalRequest.ApprovalStatus.SUBMITTED ||
            request.getStatus() == ApprovalRequest.ApprovalStatus.UNDER_REVIEW) {

            // Add to history
            List<ApprovalHistoryEntry> history = parseHistory(request.getApprovalHistory());
            history.add(ApprovalHistoryEntry.builder()
                    .action("APPROVED")
                    .userId(reviewedBy)
                    .userName(reviewedByName)
                    .comment(comment)
                    .timestamp(LocalDateTime.now())
                    .build());

            request.setApprovalHistory(toJson(history));
            request.setReviewedBy(reviewedBy);
            request.setReviewedByName(reviewedByName);
            request.setReviewedAt(LocalDateTime.now());
            request.setReviewComment(comment);

            // Check if more levels needed
            if (request.getApprovalLevel() < request.getRequiredLevels()) {
                request.setApprovalLevel(request.getApprovalLevel() + 1);
                request.setStatus(ApprovalRequest.ApprovalStatus.UNDER_REVIEW);
            } else {
                request.setStatus(ApprovalRequest.ApprovalStatus.APPROVED);
            }
        }

        return approvalRepository.save(request);
    }

    /**
     * Reject a request.
     */
    @Transactional
    public ApprovalRequest reject(Integer requestId, Integer reviewedBy, String reviewedByName, String reason) {
        ApprovalRequest request = approvalRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        if (request.getStatus() == ApprovalRequest.ApprovalStatus.SUBMITTED ||
            request.getStatus() == ApprovalRequest.ApprovalStatus.UNDER_REVIEW) {

            List<ApprovalHistoryEntry> history = parseHistory(request.getApprovalHistory());
            history.add(ApprovalHistoryEntry.builder()
                    .action("REJECTED")
                    .userId(reviewedBy)
                    .userName(reviewedByName)
                    .comment(reason)
                    .timestamp(LocalDateTime.now())
                    .build());

            request.setApprovalHistory(toJson(history));
            request.setReviewedBy(reviewedBy);
            request.setReviewedByName(reviewedByName);
            request.setReviewedAt(LocalDateTime.now());
            request.setReviewComment(reason);
            request.setStatus(ApprovalRequest.ApprovalStatus.REJECTED);
        }

        return approvalRepository.save(request);
    }

    /**
     * Request changes.
     */
    @Transactional
    public ApprovalRequest requestChanges(Integer requestId, Integer reviewedBy, String reviewedByName, String comment) {
        ApprovalRequest request = approvalRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        List<ApprovalHistoryEntry> history = parseHistory(request.getApprovalHistory());
        history.add(ApprovalHistoryEntry.builder()
                .action("CHANGES_REQUESTED")
                .userId(reviewedBy)
                .userName(reviewedByName)
                .comment(comment)
                .timestamp(LocalDateTime.now())
                .build());

        request.setApprovalHistory(toJson(history));
        request.setReviewedBy(reviewedBy);
        request.setReviewedByName(reviewedByName);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewComment(comment);
        request.setStatus(ApprovalRequest.ApprovalStatus.CHANGES_REQUESTED);

        return approvalRepository.save(request);
    }

    /**
     * Mark as applied.
     */
    @Transactional
    public ApprovalRequest markApplied(Integer requestId, Integer appliedBy) {
        ApprovalRequest request = approvalRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        request.setStatus(ApprovalRequest.ApprovalStatus.APPLIED);
        request.setAppliedAt(LocalDateTime.now());
        request.setAppliedBy(appliedBy);

        return approvalRepository.save(request);
    }

    /**
     * Get pending requests.
     */
    @Transactional(readOnly = true)
    public List<ApprovalRequest> getPendingRequests() {
        return approvalRepository.findByStatusOrderByPriorityAscCreatedAtAsc(
                ApprovalRequest.ApprovalStatus.SUBMITTED);
    }

    /**
     * Count pending.
     */
    @Transactional(readOnly = true)
    public long countPending() {
        return approvalRepository.countByStatus(ApprovalRequest.ApprovalStatus.SUBMITTED);
    }

    private List<ApprovalHistoryEntry> parseHistory(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ApprovalHistoryEntry>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse approval history", e);
            return new ArrayList<>();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @lombok.Value
    @lombok.Builder
    public static class ApprovalHistoryEntry {
        String action;
        Integer userId;
        String userName;
        String comment;
        LocalDateTime timestamp;
    }
}
