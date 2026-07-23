package com.hospital.scheduler.governance.dto;

import com.hospital.scheduler.governance.entity.AuditEvent;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Audit event DTOs.
 */
public class AuditEventDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private String entityType;
        private String entityId;
        private AuditEvent.AuditAction action;
        private String previousValue;
        private String newValue;
        private String reason;
        private String ipAddress;
        private String userAgent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String entityType;
        private String entityId;
        private String action;
        private Integer userId;
        private String userName;
        private String userRole;
        private LocalDateTime timestamp;
        private String previousValue;
        private String newValue;
        private String changeDetails;
        private String reason;
        private boolean success;
        private String errorMessage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineEvent {
        private LocalDateTime timestamp;
        private String userName;
        private String userRole;
        private String action;
        private String entityType;
        private String entityId;
        private String description;
        private String previousValue;
        private String newValue;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchRequest {
        private String entityType;
        private String entityId;
        private AuditEvent.AuditAction action;
        private Integer userId;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private Boolean success;
        private int page = 0;
        private int size = 20;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResponse {
        private java.util.List<Response> events;
        private long totalElements;
        private int totalPages;
        private int currentPage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private long totalEvents;
        private long todayEvents;
        private long weekEvents;
        private long monthEvents;
        private java.util.Map<String, Long> eventsByAction;
        private java.util.Map<String, Long> eventsByEntityType;
    }
}
