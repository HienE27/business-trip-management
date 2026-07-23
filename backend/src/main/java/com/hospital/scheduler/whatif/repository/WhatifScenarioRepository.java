package com.hospital.scheduler.whatif.repository;

import com.hospital.scheduler.whatif.entity.WhatifScenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for what-if scenarios.
 */
@Repository
public interface WhatifScenarioRepository extends JpaRepository<WhatifScenario, Integer> {

    /**
     * Find by source period ID.
     */
    List<WhatifScenario> findBySourcePeriodIdOrderByCreatedAtDesc(Integer sourcePeriodId);

    /**
     * Find baseline scenario for a period.
     */
    Optional<WhatifScenario> findBySourcePeriodIdAndBaselineTrue(Integer sourcePeriodId);

    /**
     * Find by status.
     */
    List<WhatifScenario> findByStatusOrderByCreatedAtDesc(WhatifScenario.ScenarioStatus status);

    /**
     * Find all with tags containing.
     */
    List<WhatifScenario> findByTagsContainingOrderByCreatedAtDesc(String tag);

    /**
     * Find by parent scenario ID.
     */
    List<WhatifScenario> findByParentScenarioIdOrderByCreatedAtAsc(Integer parentId);

    /**
     * Find recent scenarios.
     */
    List<WhatifScenario> findTop10ByOrderByCreatedAtDesc();
}
