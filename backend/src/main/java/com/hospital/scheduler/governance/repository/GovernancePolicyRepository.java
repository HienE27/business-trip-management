package com.hospital.scheduler.governance.repository;

import com.hospital.scheduler.governance.entity.GovernancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for governance policies.
 */
@Repository
public interface GovernancePolicyRepository extends JpaRepository<GovernancePolicy, Integer> {

    /**
     * Find active policies.
     */
    List<GovernancePolicy> findByActiveTrue();

    /**
     * Find by type.
     */
    List<GovernancePolicy> findByPolicyTypeAndActiveTrue(GovernancePolicy.PolicyType policyType);

    /**
     * Find global policies.
     */
    List<GovernancePolicy> findByGlobalTrueAndActiveTrue();

    /**
     * Find by priority range.
     */
    List<GovernancePolicy> findByActiveTrueOrderByPriorityAsc();
}
