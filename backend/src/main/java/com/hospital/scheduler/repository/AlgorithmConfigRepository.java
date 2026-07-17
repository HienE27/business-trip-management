package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.AlgorithmConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlgorithmConfigRepository extends JpaRepository<AlgorithmConfig, String> {
    Optional<AlgorithmConfig> findByParamKey(String paramKey);

    @Query("SELECT c FROM AlgorithmConfig c LEFT JOIN FETCH c.updatedBy")
    List<AlgorithmConfig> findAllWithUpdatedBy();

    /**
     * MySQL native upsert — single round-trip, immune to the SELECT-then-INSERT race
     * that produced 409 Conflict on PUT /auto-gen-config when concurrent requests
     * both observed an empty row. Ponytail: native SQL — switch to portable JPQL
     * (or a {@code @Lock(PESSIMISTIC_WRITE)} findByParamKey + save) if dialect changes.
     */
    @Modifying
    @Query(value = "INSERT INTO algorithm_config (param_key, param_value, value_type, description, created_at, updated_at) " +
            "VALUES (:paramKey, :paramValue, :valueType, :description, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
            "ON DUPLICATE KEY UPDATE " +
            "param_value = VALUES(param_value), " +
            "value_type = VALUES(value_type), " +
            "description = VALUES(description), " +
            "updated_at = CURRENT_TIMESTAMP", nativeQuery = true)
    int upsertConfig(@Param("paramKey") String paramKey,
                     @Param("paramValue") String paramValue,
                     @Param("valueType") String valueType,
                     @Param("description") String description);

    /**
     * Bulk-load all configs as a map of paramKey -> paramValue in a single SELECT.
     * Used by {@code AlgorithmConfigService} to avoid the N+1 query pattern
     * (one SELECT per getIntValue/getStringValue call) — that pattern was making
     * the algorithm-config page render take 5+ seconds.
     */
    @Query("SELECT new com.hospital.scheduler.repository.AlgorithmConfigKeyValue(c.paramKey, c.paramValue) FROM AlgorithmConfig c")
    List<com.hospital.scheduler.repository.AlgorithmConfigKeyValue> findAllAsKeyValuePairs();
}
