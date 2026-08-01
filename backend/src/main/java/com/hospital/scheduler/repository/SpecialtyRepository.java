package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpecialtyRepository extends JpaRepository<Specialty, Integer>, JpaSpecificationExecutor<Specialty> {
    Optional<Specialty> findByName(String name);
    List<Specialty> findByIsActiveTrue();

    /** Paginated query with optional keyword + status filters. */
    default org.springframework.data.domain.Page<Specialty> findPageWithFilters(
            String keyword, String status, org.springframework.data.domain.Pageable pageable) {
        return findAll((root, query, cb) -> cb.and(
                keyword == null || keyword.isBlank() ? cb.conjunction() : cb.like(cb.lower(root.get("name")), "%" + keyword.toLowerCase() + "%"),
                status == null || status.isBlank() || "all".equalsIgnoreCase(status) ? cb.conjunction() : cb.equal(root.get("isActive"), "active".equalsIgnoreCase(status) ? true : false)
        ), pageable);
    }
}
