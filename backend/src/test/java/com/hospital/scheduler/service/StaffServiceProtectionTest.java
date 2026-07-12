package com.hospital.scheduler.service;

import com.hospital.scheduler.config.CacheConfig;
import com.hospital.scheduler.dto.request.StaffRequest;
import com.hospital.scheduler.dto.response.StaffResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.ForbiddenOperationException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.AppRoleRepository;
import com.hospital.scheduler.repository.SpecialtyRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.security.AuthContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for StaffService protection rules introduced in QA bug fixes.
 *
 * Covers:
 * - BUG-C1: Cannot delete the last active admin
 * - BUG-M5: Cannot update inactive (soft-deleted) staff
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StaffService Protection Rules")
class StaffServiceProtectionTest {

    @Mock private StaffRepository staffRepository;
    @Mock private SpecialtyRepository specialtyRepository;
    @Mock private AppRoleRepository appRoleRepository;
    @Mock private AuditHistoryService auditHistoryService;
    @Mock private AuthContextService authContextService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StaffImportParser staffImportParser;
    @Mock private NotificationService notificationService;
    @Mock private CacheEvictor cacheEvictor;

    @InjectMocks
    private StaffService staffService;

    private Staff activeAdmin;
    private Staff activeStaff;
    private Staff inactiveStaff;

    private AppRole adminRole;
    private AppRole staffRole;

    @BeforeEach
    void setUp() {
        adminRole = AppRole.builder().id(1).name(RoleName.ADMIN).build();
        staffRole = AppRole.builder().id(2).name(RoleName.STAFF).build();

        activeAdmin = Staff.builder()
                .id(1).username("admin").fullName("Admin User")
                .isActive(true).status(StaffStatus.ACTIVE)
                .staffRoles(Set.of(StaffRole.builder().role(adminRole).roleId(1).build()))
                .build();

        activeStaff = Staff.builder()
                .id(2).username("doctor1").fullName("Dr. One")
                .isActive(true).status(StaffStatus.ACTIVE)
                .staffRoles(Set.of(StaffRole.builder().role(staffRole).roleId(2).build()))
                .build();

        inactiveStaff = Staff.builder()
                .id(3).username("former").fullName("Former Staff")
                .isActive(false).status(StaffStatus.INACTIVE)
                .staffRoles(Set.of(StaffRole.builder().role(staffRole).roleId(2).build()))
                .build();

        // Stub AuthContextService so audit log calls don't NPE
        when(authContextService.getCurrentStaff()).thenReturn(activeAdmin);
        // Stub NotificationService (returns NotificationResponse)
        when(notificationService.createNotification(anyInt(), any())).thenReturn(null);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // BUG-C1: Admin protection
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("BUG-C1: Admin deletion protection")
    class AdminDeletionProtection {

        @Test
        @DisplayName("Deleting last active admin → ForbiddenOperationException")
        void deleteLastAdmin_throwsForbidden() {
            when(staffRepository.findById(1)).thenReturn(Optional.of(activeAdmin));
            when(staffRepository.findByIdWithRoles(1)).thenReturn(Optional.of(activeAdmin));
            when(staffRepository.countActiveAdmins()).thenReturn(1L);

            assertThatThrownBy(() -> staffService.deleteStaff(1))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessageContaining("admin cuối cùng");
        }

        @Test
        @DisplayName("Deleting admin when 2+ active admins exist → succeeds (soft-delete)")
        void deleteAdmin_whenOthersExist_succeeds() {
            when(staffRepository.findById(1)).thenReturn(Optional.of(activeAdmin));
            when(staffRepository.findByIdWithRoles(1)).thenReturn(Optional.of(activeAdmin));
            when(staffRepository.countActiveAdmins()).thenReturn(2L);
            when(authContextService.getCurrentStaff()).thenReturn(activeAdmin);
            when(staffRepository.save(any(Staff.class))).thenAnswer(inv -> inv.getArgument(0));

            staffService.deleteStaff(1);

            verify(staffRepository).save(argThat(s -> !s.getIsActive()));
        }

        @Test
        @DisplayName("Deleting non-admin staff → succeeds")
        void deleteNonAdmin_succeeds() {
            when(staffRepository.findById(2)).thenReturn(Optional.of(activeStaff));
            when(staffRepository.findByIdWithRoles(2)).thenReturn(Optional.of(activeStaff));
            when(staffRepository.countActiveAdmins()).thenReturn(1L);
            when(authContextService.getCurrentStaff()).thenReturn(activeAdmin);
            when(staffRepository.save(any(Staff.class))).thenAnswer(inv -> inv.getArgument(0));

            staffService.deleteStaff(2);

            verify(staffRepository).save(argThat(s -> !s.getIsActive()));
        }

        @Test
        @DisplayName("Deleting non-existent staff → ResourceNotFoundException")
        void deleteNonExistent_throwsNotFound() {
            when(staffRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> staffService.deleteStaff(999))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // BUG-M5: Inactive staff update protection
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("BUG-M5: Cannot update inactive (soft-deleted) staff")
    class InactiveStaffUpdateProtection {

        @Test
        @DisplayName("Updating inactive staff → ResourceNotFoundException")
        void updateInactive_throwsNotFound() {
            when(staffRepository.findById(3)).thenReturn(Optional.of(inactiveStaff));

            StaffRequest request = StaffRequest.builder().fullName("New Name").build();

            assertThatThrownBy(() -> staffService.updateStaff(3, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("không tồn tại");
        }

        @Test
        @DisplayName("Updating active staff → succeeds")
        void updateActive_succeeds() {
            when(staffRepository.findById(2)).thenReturn(Optional.of(activeStaff));
            when(authContextService.getCurrentStaff()).thenReturn(activeAdmin);
            when(auditHistoryService.logAction(anyString(), anyInt(), any(), any(), any(), any())).thenReturn(null);
            when(notificationService.createNotification(anyInt(), any())).thenReturn(null);
            when(staffRepository.save(any(Staff.class))).thenAnswer(inv -> inv.getArgument(0));

            StaffRequest request = StaffRequest.builder().fullName("Dr. One Updated").build();

            StaffResponse result = staffService.updateStaff(2, request);

            assertThat(result.getFullName()).isEqualTo("Dr. One Updated");
        }
    }
}
