package com.hospital.scheduler.governance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.governance.dto.AuditEventDTO;
import com.hospital.scheduler.governance.dto.ConfigVersionDTO;
import com.hospital.scheduler.governance.entity.AuditEvent;
import com.hospital.scheduler.governance.entity.ConfigVersion;
import com.hospital.scheduler.governance.entity.GovernancePolicy;
import com.hospital.scheduler.governance.repository.AuditEventRepository;
import com.hospital.scheduler.governance.repository.ConfigVersionRepository;
import com.hospital.scheduler.governance.repository.GovernancePolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Central service for governance operations.
 *
 * <p>Provides:
 * <ul>
 *   <li>Config versioning and rollback</li>
 *   <li>Audit trail logging</li>
 *   <li>Policy evaluation</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GovernanceService {

    private final ConfigVersionRepository versionRepository;
    private final AuditEventRepository auditRepository;
    private final GovernancePolicyRepository policyRepository;
    private final ObjectMapper objectMapper;

    // ═══════════════════════════════════════════════════════════════════════
    // CONFIG VERSION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * BUGFIX: previously {@code Integer maxVersion = versionRepository.getMaxVersionNumber(...)} was
     * read, then {@code save(version)} happened — two concurrent calls could read the same
     * {@code maxVersion} and both write the same {@code versionNumber}, blowing the unique
     * constraint {@code (period_id, version_number)}. We now serialize per-period using a
     * JVM-scoped lock so only one createVersion per period runs at a time within this instance.
     */
    private final java.util.concurrent.ConcurrentHashMap<Integer, Object> versionLocks = new java.util.concurrent.ConcurrentHashMap<>();

    @Transactional
    public ConfigVersionDTO.Response createVersion(ConfigVersionDTO.CreateRequest request, Integer userId, String userName) {
        Integer periodId = request.getPeriodId();
        Object lock = versionLocks.computeIfAbsent(periodId, k -> new Object());
        synchronized (lock) {
            return createVersionLocked(request, userId, userName);
        }
    }

    private ConfigVersionDTO.Response createVersionLocked(ConfigVersionDTO.CreateRequest request, Integer userId, String userName) {
        // Re-read maxVersion under the lock — now safe from concurrent creators of the same period.
        Integer maxVersion = versionRepository.getMaxVersionNumber(request.getPeriodId());
        int nextVersion = (maxVersion != null ? maxVersion : 0) + 1;

        // Calculate checksum
        String checksum = calculateChecksum(request.getConfigJson());

        // Convert snapshot
        Map<String, Object> snapshot = request.getConfigSnapshot() != null
                ? request.getConfigSnapshot()
                : parseJsonToMap(request.getConfigJson());

        // Deactivate current version
        versionRepository.findByPeriodIdAndActiveTrue(request.getPeriodId())
                .ifPresent(v -> {
                    v.setActive(false);
                    versionRepository.save(v);
                });

        // Create new version
        ConfigVersion version = ConfigVersion.builder()
                .periodId(request.getPeriodId())
                .versionNumber(nextVersion)
                .configJson(request.getConfigJson())
                .configSnapshot(toJson(snapshot))
                .checksum(checksum)
                .changeComment(request.getChangeComment())
                .profileId(request.getProfileId())
                .profileName(request.getProfileName())
                .createdBy(userId)
                .createdByName(userName)
                .source(request.getSource() != null ? request.getSource() : ConfigVersion.VersionSource.MANUAL)
                .locked(false)
                .active(true)
                .build();

        version = versionRepository.save(version);

        return toVersionResponse(version);
    }

    /**
     * Get version history.
     */
    @Transactional(readOnly = true)
    public ConfigVersionDTO.HistoryResponse getVersionHistory(Integer periodId) {
        List<ConfigVersion> versions = versionRepository.findByPeriodIdOrderByVersionNumberDesc(periodId);
        Optional<ConfigVersion> current = versionRepository.findByPeriodIdAndActiveTrue(periodId);

        return ConfigVersionDTO.HistoryResponse.builder()
                .periodId(periodId)
                .versions(versions.stream().map(this::toVersionResponse).toList())
                .totalVersions(versions.size())
                .currentVersion(current.map(this::toVersionResponse).orElse(null))
                .build();
    }

    /**
     * Get diff between versions.
     */
    @Transactional(readOnly = true)
    public ConfigVersionDTO.DiffResponse getDiff(Integer fromVersionId, Integer toVersionId) {
        ConfigVersion from = versionRepository.findById(fromVersionId)
                .orElseThrow(() -> new IllegalArgumentException("From version not found"));
        ConfigVersion to = versionRepository.findById(toVersionId)
                .orElseThrow(() -> new IllegalArgumentException("To version not found"));

        Map<String, Object> fromMap = parseJsonToMap(from.getConfigSnapshot());
        Map<String, Object> toMap = parseJsonToMap(to.getConfigSnapshot());

        List<ConfigVersionDTO.ConfigDiff> diffs = new ArrayList<>();

        // Find changed and removed
        for (Map.Entry<String, Object> entry : fromMap.entrySet()) {
            String key = entry.getKey();
            Object newValue = toMap.get(key);

            if (newValue == null) {
                diffs.add(ConfigVersionDTO.ConfigDiff.builder()
                        .key(key)
                        .displayName(key)
                        .oldValue(entry.getValue())
                        .newValue(null)
                        .changeType("REMOVED")
                        .category(categorizeKey(key))
                        .build());
            } else if (!Objects.equals(entry.getValue(), newValue)) {
                diffs.add(ConfigVersionDTO.ConfigDiff.builder()
                        .key(key)
                        .displayName(key)
                        .oldValue(entry.getValue())
                        .newValue(newValue)
                        .changeType("CHANGED")
                        .category(categorizeKey(key))
                        .build());
            }
        }

        // Find added
        for (Map.Entry<String, Object> entry : toMap.entrySet()) {
            if (!fromMap.containsKey(entry.getKey())) {
                diffs.add(ConfigVersionDTO.ConfigDiff.builder()
                        .key(entry.getKey())
                        .displayName(entry.getKey())
                        .oldValue(null)
                        .newValue(entry.getValue())
                        .changeType("ADDED")
                        .category(categorizeKey(entry.getKey()))
                        .build());
            }
        }

        return ConfigVersionDTO.DiffResponse.builder()
                .fromVersionId(fromVersionId)
                .toVersionId(toVersionId)
                .diffs(diffs)
                .addedCount((int) diffs.stream().filter(d -> "ADDED".equals(d.getChangeType())).count())
                .removedCount((int) diffs.stream().filter(d -> "REMOVED".equals(d.getChangeType())).count())
                .changedCount((int) diffs.stream().filter(d -> "CHANGED".equals(d.getChangeType())).count())
                .build();
    }

    /**
     * Rollback to a previous version.
     */
    @Transactional
    public ConfigVersionDTO.Response rollback(ConfigVersionDTO.RollbackRequest request, Integer userId, String userName) {
        ConfigVersion target = versionRepository.findById(request.getTargetVersionId())
                .orElseThrow(() -> new IllegalArgumentException("Target version not found"));

        // Create new version with target's config
        ConfigVersionDTO.CreateRequest createRequest = ConfigVersionDTO.CreateRequest.builder()
                .periodId(target.getPeriodId())
                .configJson(target.getConfigJson())
                .configSnapshot(parseJsonToMap(target.getConfigSnapshot()))
                .changeComment("Rollback to version " + target.getVersionNumber() + ": " + request.getReason())
                .source(ConfigVersion.VersionSource.ROLLBACK)
                .build();

        return createVersion(createRequest, userId, userName);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AUDIT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Log an audit event.
     */
    @Transactional
    public void logAudit(AuditEventDTO.CreateRequest request, Integer userId, String userName, String userRole) {
        Integer entityId = null;
        if (request.getEntityId() != null) {
            try {
                entityId = Integer.parseInt(request.getEntityId().toString());
            } catch (NumberFormatException ignored) {}
        }
        
        AuditEvent event = AuditEvent.builder()
                .entityType(request.getEntityType())
                .entityId(entityId)
                .action(request.getAction())
                .staffId(userId)
                .details(request.getNewValue())
                .ipAddress(request.getIpAddress())
                .build();

        auditRepository.save(event);
    }

    /**
     * Search audit events.
     */
    @Transactional(readOnly = true)
    public AuditEventDTO.SearchResponse searchAudit(AuditEventDTO.SearchRequest request) {
        PageRequest pageRequest = PageRequest.of(request.getPage(), request.getSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AuditEvent> page;

        if (request.getEntityType() != null && request.getEntityId() != null) {
            page = auditRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                    request.getEntityType(), request.getEntityId(), pageRequest);
        } else if (request.getUserId() != null) {
            page = auditRepository.findByStaffIdOrderByCreatedAtDesc(request.getUserId(), pageRequest);
        } else if (request.getAction() != null) {
            page = auditRepository.findByActionOrderByCreatedAtDesc(request.getAction(), pageRequest);
        } else if (request.getFromDate() != null && request.getToDate() != null) {
            page = auditRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                    request.getFromDate(), request.getToDate(), pageRequest);
        } else {
            page = auditRepository.findAll(pageRequest);
        }

        return AuditEventDTO.SearchResponse.builder()
                .events(page.getContent().stream().map(this::toAuditResponse).toList())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber())
                .build();
    }

    /**
     * Get audit summary.
     */
    @Transactional(readOnly = true)
    public AuditEventDTO.Summary getAuditSummary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        LocalDateTime weekStart = today.minusDays(now.getDayOfWeek().getValue() - 1);
        LocalDateTime monthStart = today.withDayOfMonth(1);

        Map<String, Long> byAction = new LinkedHashMap<>();
        for (Object[] row : auditRepository.countByActionGroup()) {
            byAction.put(((AuditEvent.AuditAction) row[0]).name(), (Long) row[1]);
        }

        Map<String, Long> byEntity = new LinkedHashMap<>();
        for (Object[] row : auditRepository.countByEntityTypeGroup()) {
            byEntity.put((String) row[0], (Long) row[1]);
        }

        return AuditEventDTO.Summary.builder()
                .totalEvents(auditRepository.count())
                .todayEvents(auditRepository.countToday(today))
                .weekEvents(auditRepository.countThisWeek(weekStart))
                .monthEvents(auditRepository.countThisMonth(monthStart))
                .eventsByAction(byAction)
                .eventsByEntityType(byEntity)
                .build();
    }

    /**
     * Get timeline events for an entity.
     */
    @Transactional(readOnly = true)
    public List<AuditEventDTO.TimelineEvent> getTimeline(String entityType, String entityId) {
        List<AuditEvent> events = auditRepository
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, PageRequest.of(0, 100))
                .getContent();

        return events.stream().map(this::toTimelineEvent).toList();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POLICY
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Evaluate all active policies for a context.
     */
    @Transactional(readOnly = true)
    public List<PolicyEvaluationResult> evaluatePolicies(PolicyContext context) {
        List<GovernancePolicy> policies = policyRepository.findByActiveTrueOrderByPriorityAsc();
        List<PolicyEvaluationResult> results = new ArrayList<>();

        for (GovernancePolicy policy : policies) {
            if (isPolicyApplicable(policy, context)) {
                results.add(evaluatePolicy(policy, context));
            }
        }

        return results;
    }

    /**
     * Create a new policy.
     */
    @Transactional
    public GovernancePolicy createPolicy(GovernancePolicy policy, Integer userId) {
        policy.setCreatedBy(userId);
        return policyRepository.save(policy);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private String calculateChecksum(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString();
        }
    }

    private Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON", e);
            return Collections.emptyMap();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String categorizeKey(String key) {
        if (key.contains("tabu") || key.contains("Tabu")) return "SEARCH";
        if (key.contains("coverage") || key.contains("Coverage")) return "COVERAGE";
        if (key.contains("fairness") || key.contains("Fairness")) return "FAIRNESS";
        if (key.contains("max") || key.contains("Max")) return "CONSTRAINT";
        if (key.contains("weight") || key.contains("Weight")) return "WEIGHT";
        return "OTHER";
    }

    private boolean isPolicyApplicable(GovernancePolicy policy, PolicyContext context) {
        // Check date range
        LocalDateTime now = LocalDateTime.now();
        if (policy.getEffectiveFrom() != null && policy.getEffectiveFrom().isAfter(now)) return false;
        if (policy.getEffectiveTo() != null && policy.getEffectiveTo().isBefore(now)) return false;

        // Check staff target
        if (!policy.isGlobal() && context.getStaffId() != null) {
            try {
                List<Integer> targetIds = objectMapper.readValue(
                        policy.getTargetStaffIds(), new TypeReference<List<Integer>>() {});
                if (!targetIds.contains(context.getStaffId())) return false;
            } catch (JsonProcessingException e) {
                return false;
            }
        }

        return true;
    }

    private PolicyEvaluationResult evaluatePolicy(GovernancePolicy policy, PolicyContext context) {
        return PolicyEvaluationResult.builder()
                .policyId(policy.getId())
                .policyName(policy.getName())
                .policyType(policy.getPolicyType().name())
                .action(policy.getAction().name())
                .actionValue(policy.getActionValue())
                .priority(policy.getPriority())
                .triggered(true)
                .build();
    }

    private ConfigVersionDTO.Response toVersionResponse(ConfigVersion v) {
        return ConfigVersionDTO.Response.builder()
                .id(v.getId() != null ? v.getId().intValue() : null)
                .periodId(v.getPeriodId())
                .versionNumber(v.getVersionNumber())
                .versionLabel(v.getVersionLabel())
                .configJson(v.getConfigJson())
                .configSnapshot(parseJsonToMap(v.getConfigSnapshot()))
                .checksum(v.getChecksum())
                .changeComment(v.getChangeComment())
                .profileId(v.getProfileId())
                .profileName(v.getProfileName())
                .createdBy(v.getCreatedBy())
                .createdByName(v.getCreatedByName())
                .createdAt(v.getCreatedAt())
                .locked(Boolean.TRUE.equals(v.getLocked()))
                .active(Boolean.TRUE.equals(v.getActive()))
                .source(v.getSource() != null ? v.getSource().name() : null)
                .build();
    }

    private AuditEventDTO.Response toAuditResponse(AuditEvent e) {
        return AuditEventDTO.Response.builder()
                .id(e.getId())
                .entityType(e.getEntityType())
                .entityId(e.getEntityId() != null ? e.getEntityId().toString() : null)
                .action(e.getAction() != null ? e.getAction().name() : null)
                .userId(e.getStaffId())
                .userName(null) // Not available in governance.AuditEvent
                .userRole(null) // Not available in governance.AuditEvent
                .timestamp(e.getCreatedAt())
                .previousValue(null) // Not available
                .newValue(e.getDetails())
                .changeDetails(null) // Not available
                .reason(null) // Not available
                .success(true)
                .errorMessage(null)
                .build();
    }

    private AuditEventDTO.TimelineEvent toTimelineEvent(AuditEvent e) {
        return AuditEventDTO.TimelineEvent.builder()
                .timestamp(e.getCreatedAt())
                .userName(null) // Not available
                .userRole(null) // Not available
                .action(e.getAction() != null ? e.getAction().name() : null)
                .entityType(e.getEntityType())
                .entityId(e.getEntityId() != null ? e.getEntityId().toString() : null)
                .description(formatActionDescription(e))
                .previousValue(null) // Not available
                .newValue(e.getDetails())
                .reason(null) // Not available
                .build();
    }

    private String formatActionDescription(AuditEvent e) {
        String action = e.getAction() != null ? e.getAction().name().replace("_", " ").toLowerCase() : "unknown";
        return action + " " + e.getEntityType();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════════════

    @lombok.Value
    @lombok.Builder
    public static class PolicyContext {
        Integer staffId;
        String staffName;
        String shiftType;
        LocalDateTime workDate;
        Integer periodId;
    }

    @lombok.Value
    @lombok.Builder
    public static class PolicyEvaluationResult {
        Integer policyId;
        String policyName;
        String policyType;
        String action;
        String actionValue;
        Integer priority;
        boolean triggered;
    }
}
