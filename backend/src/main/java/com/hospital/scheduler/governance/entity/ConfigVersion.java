package com.hospital.scheduler.governance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for configuration versioning in governance module.
 */
@Entity
@Table(name = "config_version", indexes = {
    @Index(name = "idx_config_version_period", columnList = "period_id"),
    @Index(name = "idx_config_version_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Period ID this config belongs to.
     */
    @Column(name = "period_id", nullable = false)
    private Integer periodId;

    /**
     * Version number (sequential per period).
     */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /**
     * Version label (optional).
     */
    @Column(name = "version_label")
    private String versionLabel;

    /**
     * Configuration JSON snapshot.
     */
    @Column(name = "config_json", columnDefinition = "LONGTEXT")
    private String configJson;

    /**
     * Configuration as key-value map (extracted).
     */
    @Column(name = "config_snapshot", columnDefinition = "JSON")
    private String configSnapshot;

    /**
     * MD5 checksum for integrity verification.
     */
    @Column(name = "checksum", length = 64)
    private String checksum;

    /**
     * Change summary/comment.
     */
    @Column(name = "change_comment", columnDefinition = "TEXT")
    private String changeComment;

    /**
     * Profile ID used (if any).
     */
    @Column(name = "profile_id")
    private Integer profileId;

    /**
     * Profile name used.
     */
    @Column(name = "profile_name")
    private String profileName;

    /**
     * User who created this version.
     */
    @Column(name = "created_by")
    private Integer createdBy;

    /**
     * Username who created this version.
     */
    @Column(name = "created_by_name")
    private String createdByName;

    /**
     * Creation timestamp.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last update timestamp.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Whether this version is locked (cannot be deleted).
     */
    @Column(name = "is_locked", nullable = false)
    @Builder.Default
    private Boolean locked = false;

    /**
     * Whether this is the current active version.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Source of this version (MANUAL, PROFILE, ROLLBACK, IMPORT).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    @Builder.Default
    private VersionSource source = VersionSource.MANUAL;

    /**
     * Version sources.
     */
    public enum VersionSource {
        MANUAL,
        PROFILE,
        ROLLBACK,
        IMPORT,
        AUTO_SAVE,
        APPROVAL
    }
}
