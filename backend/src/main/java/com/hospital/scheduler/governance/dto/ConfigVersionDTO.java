package com.hospital.scheduler.governance.dto;

import com.hospital.scheduler.governance.entity.ConfigVersion;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Config version DTOs.
 */
public class ConfigVersionDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private Integer periodId;
        private String configJson;
        private Map<String, Object> configSnapshot;
        private String changeComment;
        private Integer profileId;
        private String profileName;
        private ConfigVersion.VersionSource source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Integer id;
        private Integer periodId;
        private Integer versionNumber;
        private String versionLabel;
        private String configJson;
        private Map<String, Object> configSnapshot;
        private String checksum;
        private String changeComment;
        private Integer profileId;
        private String profileName;
        private Integer createdBy;
        private String createdByName;
        private LocalDateTime createdAt;
        private boolean locked;
        private boolean active;
        private String source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiffResponse {
        private Integer fromVersionId;
        private Integer toVersionId;
        private List<ConfigDiff> diffs;
        private int addedCount;
        private int removedCount;
        private int changedCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigDiff {
        private String key;
        private String displayName;
        private Object oldValue;
        private Object newValue;
        private String changeType; // ADDED, REMOVED, CHANGED
        private String category;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RollbackRequest {
        private Integer targetVersionId;
        private String reason;
        private boolean createSnapshot;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryResponse {
        private Integer periodId;
        private List<Response> versions;
        private int totalVersions;
        private Response currentVersion;
    }
}
