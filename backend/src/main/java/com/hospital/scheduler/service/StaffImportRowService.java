package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.AppRole;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.RoleName;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.StaffRole;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.repository.AppRoleRepository;
import com.hospital.scheduler.repository.SpecialtyRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.security.AuthContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper bean that persists ONE staff row inside its own transaction.
 *
 * <p>Lives in a separate Spring bean (rather than a private method on
 * {@link StaffService}) so that the {@code @Transactional(REQUIRES_NEW)} proxy is
 * honored — Spring's transactional advice only fires when a method is called
 * through the proxy, not via {@code this.method()}.
 *
 * <p>This is what allows {@code StaffService.importStaffs} to keep going on a
 * per-row failure (e.g. duplicate username, invalid role name) without rolling
 * back the entire 1k-row batch — BUGFIX for audit Bug BE#9.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffImportRowService {

    private final StaffRepository staffRepository;
    private final AppRoleRepository appRoleRepository;
    private final SpecialtyRepository specialtyRepository;
    private final AuditHistoryService auditHistoryService;
    private final AuthContextService authContextService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Result of a single-row import attempt.
     *
     * @param saved the saved Staff (id assigned) on success, or null on failure
     * @param isNew true when this was a fresh insert, false when an existing row was updated
     * @param error non-null error message when saved == null
     */
    public record RowResult(Staff saved, boolean isNew, String error) {
        public static RowResult failure(String error) {
            return new RowResult(null, false, error);
        }
        public static RowResult success(Staff saved, boolean isNew) {
            return new RowResult(saved, isNew, null);
        }
        public boolean isSuccess() { return saved != null; }
    }

    /**
     * Persist a single staff row in its own transaction. Any exception is caught
     * and converted to a {@link RowResult#failure(String)} so the caller can
     * continue with the next row instead of rolling back the whole import.
     *
     * @param newRow         the parsed staff record (must already have a temp password set)
     * @param rolesToAssign  role names to attach to the staff row (may be null)
     * @param existingByCode existing staff keyed by staffCode (may be null if insert-only)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = Exception.class)
    public RowResult saveRow(Staff newRow, List<String> rolesToAssign,
                             java.util.Map<String, Staff> existingByCode) {
        try {
            // Decide insert vs update by staffCode lookup
            Staff existing = existingByCode == null ? null : existingByCode.get(newRow.getStaffCode());
            Staff saved;
            boolean isNew;
            if (existing != null) {
                existing.setFullName(newRow.getFullName());
                existing.setPhone(newRow.getPhone());
                existing.setEmail(newRow.getEmail());
                existing.setSpecialty(newRow.getSpecialty());
                existing.setMaxShiftsPerMonth(newRow.getMaxShiftsPerMonth());
                existing.setIsActive(newRow.getIsActive());
                existing.setStatus(newRow.getStatus());
                if (newRow.getPasswordHash() != null) {
                    existing.setPasswordHash(newRow.getPasswordHash());
                }
                saved = staffRepository.save(existing);
                isNew = false;
            } else {
                if (newRow.getUsername() != null
                        && staffRepository.existsByUsername(newRow.getUsername())) {
                    return RowResult.failure("Username '" + newRow.getUsername() + "' đã tồn tại");
                }
                saved = staffRepository.save(newRow);
                isNew = true;
            }

            // Attach roles (best-effort — invalid role names fail the whole row)
            if (rolesToAssign != null && !rolesToAssign.isEmpty()) {
                Set<Integer> desired = new HashSet<>();
                for (String roleName : rolesToAssign) {
                    AppRole role = appRoleRepository
                            .findByName(RoleName.valueOf(roleName.toUpperCase()))
                            .orElse(null);
                    if (role == null) {
                        return RowResult.failure("Không tìm thấy role: " + roleName);
                    }
                    desired.add(role.getId());
                }
                // Drop roles not in the desired set, then add missing ones
                saved.getStaffRoles().removeIf(sr -> !desired.contains(sr.getRoleId()));
                Set<Integer> current = saved.getStaffRoles().stream()
                        .map(StaffRole::getRoleId).collect(java.util.stream.Collectors.toSet());
                for (Integer roleId : desired) {
                    if (!current.contains(roleId)) {
                        saved.getStaffRoles().add(StaffRole.builder()
                                .staffId(saved.getId())
                                .roleId(roleId)
                                .build());
                    }
                }
                saved = staffRepository.save(saved);
            }

            // Best-effort audit — never fail the import for an audit miss
            try {
                Integer actorId = null;
                try {
                    actorId = authContextService.getCurrentStaff().getId();
                } catch (Exception ignored) { /* background / seed caller */ }
                AuditHistory.ActionType action = isNew
                        ? AuditHistory.ActionType.INSERT
                        : AuditHistory.ActionType.UPDATE;
                auditHistoryService.logAction("staff", saved.getId(), action, null, saved, actorId);
            } catch (Exception auditEx) {
                log.warn("Audit for staff {} {} skipped: {}",
                        isNew ? "create" : "update", saved.getId(), auditEx.getMessage());
            }

            return RowResult.success(saved, isNew);
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            return RowResult.failure("Trùng dữ liệu (username/email): " + dup.getMostSpecificCause().getMessage());
        } catch (Exception ex) {
            log.warn("Staff import row failed: {}", ex.getMessage(), ex);
            return RowResult.failure(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /**
     * Re-resolve the {@code Specialty} from an ID so the caller can build a
     * detached Staff row that survives the per-row transaction boundary.
     */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Specialty findSpecialty(Integer id) {
        if (id == null) return null;
        return specialtyRepository.findById(id).orElse(null);
    }
}
