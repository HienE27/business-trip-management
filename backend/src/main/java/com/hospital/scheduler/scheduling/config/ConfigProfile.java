package com.hospital.scheduler.scheduling.config;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Configuration profile — a saved snapshot of ConfigDomain.
 *
 * <p>Profiles allow Admin to:
 * <ul>
 *   <li>Save different config configurations</li>
 *   <li>Compare profiles before applying</li>
 *   <li>Duplicate and customize system profiles</li>
 *   <li>Export/import profiles</li>
 * </ul>
 *
 * <p>System profiles (isSystem=true) cannot be edited or deleted.
 * They serve as templates for creating custom profiles.
 */
@Entity
@Table(name = "config_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique profile key (e.g., "balanced", "emergency", "holiday").
     * Used in URLs and API calls.
     */
    @Column(name = "profile_key", unique = true, nullable = false, length = 64)
    private String profileKey;

    /**
     * Display name in Vietnamese.
     */
    @Column(name = "name_vi", nullable = false, length = 128)
    private String nameVi;

    /**
     * Display name in English.
     */
    @Column(name = "name_en", length = 128)
    private String nameEn;

    /**
     * Detailed description of what this profile is optimized for.
     */
    @Column(name = "description", length = 512)
    private String description;

    /**
     * Profile category for grouping.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 32)
    private ProfileCategory category;

    /**
     * Material icon name for UI display.
     */
    @Column(name = "icon", length = 64)
    private String icon;

    /**
     * Tags for filtering and search.
     * Stored as JSON array: ["production", "emergency", "testing"]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "json")
    private String[] tags;

    /**
     * Whether this is a system-provided profile.
     * System profiles cannot be edited or deleted.
     */
    @Column(name = "is_system", nullable = false)
    private boolean isSystem;

    /**
     * Whether this is the default profile selected when no profile is active.
     */
    @Column(name = "is_default")
    private boolean isDefault;

    /**
     * Whether this profile is marked as favorite.
     */
    @Column(name = "is_favorite")
    private boolean isFavorite;

    /**
     * The serialized ConfigDomain as JSON.
     * This is the full configuration snapshot.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", columnDefinition = "json", nullable = false)
    private String configJson;

    /**
     * Username of the user who created this profile.
     */
    @Column(name = "created_by", length = 128)
    private String createdBy;

    /**
     * When this profile was created.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * When this profile was last updated.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Profile categories for grouping.
     */
    public enum ProfileCategory {
        GENERAL("Tổng quát", "General"),
        ALGORITHM("Thuật toán", "Algorithm"),
        FAIRNESS("Công bằng", "Fairness"),
        COVERAGE("Phủ sóng", "Coverage"),
        EMERGENCY("Khẩn cấp", "Emergency"),
        HOLIDAY("Ngày nghỉ", "Holiday"),
        TESTING("Thử nghiệm", "Testing");

        public final String labelVi;
        public final String labelEn;

        ProfileCategory(String labelVi, String labelEn) {
            this.labelVi = labelVi;
            this.labelEn = labelEn;
        }
    }
}
