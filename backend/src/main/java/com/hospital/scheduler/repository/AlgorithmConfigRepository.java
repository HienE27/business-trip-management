package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.AlgorithmConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlgorithmConfigRepository extends JpaRepository<AlgorithmConfig, String> {
    Optional<AlgorithmConfig> findByParamKey(String paramKey);

    @Query("SELECT c FROM AlgorithmConfig c LEFT JOIN FETCH c.updatedBy")
    List<AlgorithmConfig> findAllWithUpdatedBy();

    /**
     * Bulk-load all configs as a map of paramKey -> paramValue in a single SELECT.
     * Used by {@code AlgorithmConfigService} to avoid the N+1 query pattern
     * (one SELECT per getIntValue/getStringValue call) — that pattern was making
     * the algorithm-config page render take 5+ seconds.
     */
    @Query("SELECT new com.hospital.scheduler.repository.AlgorithmConfigKeyValue(c.paramKey, c.paramValue) FROM AlgorithmConfig c")
    List<com.hospital.scheduler.repository.AlgorithmConfigKeyValue> findAllAsKeyValuePairs();
}
