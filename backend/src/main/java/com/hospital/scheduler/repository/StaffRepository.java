package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Integer> {
    @Query("SELECT s FROM Staff s LEFT JOIN FETCH s.staffRoles sr LEFT JOIN FETCH sr.role WHERE s.username = :username")
    Optional<Staff> findByUsername(@Param("username") String username);
    Optional<Staff> findByEmail(String email);
    List<Staff> findByIsActiveTrue();
    List<Staff> findBySpecialtyId(Integer specialtyId);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    @Query("SELECT s FROM Staff s LEFT JOIN FETCH s.staffRoles WHERE s.id = :id")
    Optional<Staff> findByIdWithRoles(Integer id);

    @Query("SELECT DISTINCT s FROM Staff s LEFT JOIN FETCH s.specialty LEFT JOIN FETCH s.staffRoles sr LEFT JOIN FETCH sr.role " +
           "WHERE (:keyword IS NULL OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(s.username) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:specialtyId IS NULL OR s.specialty.id = :specialtyId) " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (:role IS NULL OR UPPER(sr.role.name) = UPPER(:role))")
    List<Staff> searchStaffs(@Param("keyword") String keyword,
                              @Param("specialtyId") Integer specialtyId,
                              @Param("status") String status,
                              @Param("role") String role);
}
