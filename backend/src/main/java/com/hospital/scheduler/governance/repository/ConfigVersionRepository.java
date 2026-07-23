package com.hospital.scheduler.governance.repository;

import com.hospital.scheduler.governance.entity.ConfigVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for config versions.
 */
@Repository
public interface ConfigVersionRepository extends JpaRepository<ConfigVersion, Integer> {

    /**
     * Find versions by period ID.
     */
    List<ConfigVersion> findByPeriodIdOrderByVersionNumberDesc(Integer periodId);

    /**
     * Find active version for a period.
     */
    Optional<ConfigVersion> findByPeriodIdAndActiveTrue(Integer periodId);

    /**
     * Find by period and version number.
     */
    Optional<ConfigVersion> findByPeriodIdAndVersionNumber(Integer periodId, Integer versionNumber);

    /**
     * Get latest version number for a period.
     */
    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM ConfigVersion v WHERE v.periodId = :periodId")
    Integer getMaxVersionNumber(Integer periodId);

    /**
     * Find locked versions.
     */
    List<ConfigVersion> findByPeriodIdAndLockedTrue(Integer periodId);

    /**
     * Find recent versions.
     */
    List<ConfigVersion> findTop10ByOrderByCreatedAtDesc();
}
