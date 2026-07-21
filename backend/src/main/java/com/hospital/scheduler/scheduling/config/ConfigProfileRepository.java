package com.hospital.scheduler.scheduling.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ConfigProfile entity.
 */
@Repository
public interface ConfigProfileRepository extends JpaRepository<ConfigProfile, Long> {

    /**
     * Find profile by unique key.
     */
    Optional<ConfigProfile> findByProfileKey(String profileKey);

    /**
     * Check if profile key exists.
     */
    boolean existsByProfileKey(String profileKey);

    /**
     * Find all system profiles.
     */
    List<ConfigProfile> findByIsSystemTrue();

    /**
     * Find all non-system (custom) profiles.
     */
    List<ConfigProfile> findByIsSystemFalse();

    /**
     * Find all profiles in a category.
     */
    List<ConfigProfile> findByCategory(ConfigProfile.ProfileCategory category);

    /**
     * Find default profile.
     */
    Optional<ConfigProfile> findByIsDefaultTrue();

    /**
     * Find favorite profiles.
     */
    List<ConfigProfile> findByIsFavoriteTrue();

    /**
     * Search profiles by name (case-insensitive).
     */
    @Query("SELECT p FROM ConfigProfile p WHERE LOWER(p.nameVi) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.nameEn) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<ConfigProfile> searchByName(String query);

    /**
     * Find profiles containing a tag.
     */
    @Query(value = "SELECT * FROM config_profile WHERE JSON_CONTAINS(tags, :tag)", nativeQuery = true)
    List<ConfigProfile> findByTag(String tag);

    /**
     * Clear default flag from all profiles.
     * Uses clearAutomatically to evict cached entities so callers see fresh
     * values after this bulk update.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ConfigProfile p SET p.isDefault = false WHERE p.isDefault = true")
    void clearAllDefaults();

    /**
     * Clear favorite flag from all profiles.
     * Uses clearAutomatically to evict cached entities so callers see fresh
     * values after this bulk update.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ConfigProfile p SET p.isFavorite = false WHERE p.isFavorite = true")
    void clearAllFavorites();
}
