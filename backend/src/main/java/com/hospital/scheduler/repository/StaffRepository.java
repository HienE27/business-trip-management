package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.Staff;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Integer> {
    @Query("SELECT s FROM Staff s LEFT JOIN FETCH s.staffRoles sr LEFT JOIN FETCH sr.role WHERE s.username = :username")
    Optional<Staff> findByUsername(@Param("username") String username);
    Optional<Staff> findByEmail(String email);
    List<Staff> findByIsActiveTrue();
    long countByIsActiveTrue();
    List<Staff> findBySpecialtyId(Integer specialtyId);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByStaffCode(String staffCode);
    @Query("SELECT s FROM Staff s LEFT JOIN FETCH s.staffRoles WHERE s.id = :id")
    Optional<Staff> findByIdWithRoles(Integer id);

    @Query("SELECT DISTINCT s FROM Staff s LEFT JOIN FETCH s.specialty LEFT JOIN FETCH s.staffRoles sr LEFT JOIN FETCH sr.role " +
           "WHERE (:keyword IS NULL OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(s.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(s.staffCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:specialtyId IS NULL OR s.specialty.id = :specialtyId) " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (:role IS NULL OR (sr.role IS NOT NULL AND UPPER(sr.role.name) = UPPER(:role))) " +
           "AND (:position IS NULL OR LOWER(s.position) LIKE LOWER(CONCAT('%', :position, '%')))")
    List<Staff> searchStaffs(@Param("keyword") String keyword,
                              @Param("specialtyId") Integer specialtyId,
                              @Param("status") String status,
                              @Param("role") String role,
                              @Param("position") String position);

    /**
     * Pageable variant of {@link #searchStaffs} for the /staff/search/paginated endpoint.
     * Drops JOIN FETCH so Hibernate can emit a proper count query (otherwise it
     * falls back to in-memory pagination and the count is wrong).
     */
    @Query("SELECT DISTINCT s FROM Staff s LEFT JOIN s.specialty LEFT JOIN s.staffRoles sr LEFT JOIN sr.role " +
           "WHERE (:keyword IS NULL OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(s.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(s.staffCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:specialtyId IS NULL OR s.specialty.id = :specialtyId) " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (:role IS NULL OR (sr.role IS NOT NULL AND UPPER(sr.role.name) = UPPER(:role))) " +
           "AND (:position IS NULL OR LOWER(s.position) LIKE LOWER(CONCAT('%', :position, '%')))")
    Page<Staff> searchStaffs(@Param("keyword") String keyword,
                              @Param("specialtyId") Integer specialtyId,
                              @Param("status") String status,
                              @Param("role") String role,
                              @Param("position") String position,
                              Pageable pageable);

    @Query("SELECT DISTINCT s FROM Staff s LEFT JOIN FETCH s.staffRoles sr LEFT JOIN FETCH sr.role " +
           "WHERE sr.role.name IN ('MANAGER', 'ADMIN')")
    List<Staff> findManagers();

    @Query("SELECT DISTINCT s FROM Staff s LEFT JOIN FETCH s.specialty LEFT JOIN FETCH s.staffRoles sr LEFT JOIN FETCH sr.role")
    List<Staff> findAllWithRoles();

    /**
     * Find the maximum numeric suffix of staff codes with the given prefix.
     * Uses database-level MAX for efficiency instead of loading all staff.
     * Returns 0 if no matching codes exist.
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(staff_code, :prefixLen + 1) AS UNSIGNED)), 0) " +
                   "FROM staff WHERE staff_code LIKE CONCAT(:prefix, '%')", nativeQuery = true)
    int findMaxStaffCodeNumber(@Param("prefix") String prefix, @Param("prefixLen") int prefixLen);

    /**
     * Find staff by IDs with roles pre-fetched for efficient batch lookups.
     * Avoids N+1 queries when validating multiple schedules.
     */
    @Query("SELECT s FROM Staff s LEFT JOIN FETCH s.staffRoles sr LEFT JOIN FETCH sr.role WHERE s.id IN :ids")
    List<Staff> findByIdsWithRoles(@Param("ids") List<Integer> ids);

    /**
     * Backfill null staff_codes for existing records.
     * Uses Java-level iteration to avoid MySQL self-referencing UPDATE limitation.
     * Safe to run repeatedly — only updates NULL values.
     */
    @Query("SELECT s FROM Staff s WHERE s.staffCode IS NULL ORDER BY s.id")
    List<Staff> findByStaffCodeIsNull();

    long countByStatus(com.hospital.scheduler.entity.StaffStatus status);

    /**
     * Count active staff holding the ADMIN role.
     * Used by StaffService to block removal/demotion of the last active admin
     * — without this guard the system can be left without any administrator.
     */
    @Query("SELECT COUNT(DISTINCT s) FROM Staff s JOIN s.staffRoles sr JOIN sr.role r " +
           "WHERE s.isActive = true AND r.name = com.hospital.scheduler.entity.RoleName.ADMIN")
    long countActiveAdmins();

    /**
     * Check whether the given staff ID currently holds the ADMIN role.
     * Returns false for staff with no roles or who only hold non-admin roles.
     */
    @Query("SELECT (COUNT(sr) > 0) FROM Staff s JOIN s.staffRoles sr JOIN sr.role r " +
           "WHERE s.id = :staffId AND r.name = com.hospital.scheduler.entity.RoleName.ADMIN")
    boolean hasAdminRole(@Param("staffId") Integer staffId);
}
