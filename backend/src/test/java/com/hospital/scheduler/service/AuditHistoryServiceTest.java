package com.hospital.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.AuditHistoryRepository;
import com.hospital.scheduler.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuditHistoryService Tests - Ghi log thao tác")
class AuditHistoryServiceTest {

    @Mock private AuditHistoryRepository auditHistoryRepository;
    @Mock private StaffRepository staffRepository;
    private ObjectMapper objectMapper;

    private AuditHistoryService service;

    private Staff adminStaff;
    private AuditHistory testLog;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new AuditHistoryService(auditHistoryRepository, staffRepository, objectMapper);

        adminStaff = Staff.builder()
                .id(1).username("admin").fullName("Admin User").isActive(true).build();
        adminStaff.setStaffRoles(new java.util.HashSet<>());

        testLog = AuditHistory.builder()
                .id(1)
                .tableName("schedule")
                .recordId(100)
                .actionType(AuditHistory.ActionType.INSERT)
                .changedBy(adminStaff)
                .oldData(null)
                .newData("{\"id\":100}")
                .createdAt(java.time.LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("logAction -> lưu record với oldData/newData serialized JSON")
    void logAction_shouldSerializeData() {
        when(staffRepository.findById(1)).thenReturn(Optional.of(adminStaff));
        when(auditHistoryRepository.save(any(AuditHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> newData = Map.of("id", 100, "staffId", 1);
        service.logAction("schedule", 100, AuditHistory.ActionType.INSERT, null, newData, 1);

        ArgumentCaptor<AuditHistory> captor = ArgumentCaptor.forClass(AuditHistory.class);
        verify(auditHistoryRepository).save(captor.capture());
        AuditHistory saved = captor.getValue();
        assertThat(saved.getTableName()).isEqualTo("schedule");
        assertThat(saved.getRecordId()).isEqualTo(100);
        assertThat(saved.getChangedBy()).isEqualTo(adminStaff);
        assertThat(saved.getOldData()).isNull();
        assertThat(saved.getNewData()).contains("\"id\":100");
    }

    @Test
    @DisplayName("logAction với changedById=null -> changedBy=null")
    void logAction_nullUser() {
        when(auditHistoryRepository.save(any(AuditHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.logAction("schedule", 100, AuditHistory.ActionType.DELETE, testLog, null, null);

        ArgumentCaptor<AuditHistory> captor = ArgumentCaptor.forClass(AuditHistory.class);
        verify(auditHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getChangedBy()).isNull();
    }

    @Test
    @DisplayName("getAllAuditHistory -> trả về Page")
    void getAll() {
        Pageable pageable = PageRequest.of(0, 50);
        when(auditHistoryRepository.findAllWithChangedBy(pageable))
                .thenReturn(new PageImpl<>(List.of(testLog), pageable, 1));

        var result = service.getAllAuditHistory(0, 50);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getAuditHistoryByTableAndRecord với recordId Integer")
    void getByTableAndRecord() {
        Pageable pageable = PageRequest.of(0, 50);
        when(auditHistoryRepository.findByTableNameAndRecordIdWithChangedBy("schedule", 100))
                .thenReturn(List.of(testLog));

        var result = service.getAuditHistoryByTableAndRecord("schedule", 100);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getAuditHistoryByTableAndRecord với recordId String")
    void getByTableAndRecord_stringId() {
        when(auditHistoryRepository.findByTableNameAndRecordIdWithChangedBy("schedule", 100))
                .thenReturn(List.of(testLog));

        var result = service.getAuditHistoryByTableAndRecord("schedule", "100");

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getAuditHistoryByUser + byDateRange")
    void getByUserAndDate() {
        Pageable pageable = PageRequest.of(0, 50);
        when(auditHistoryRepository.findByChangedBy(1)).thenReturn(List.of(testLog));
        when(auditHistoryRepository.findByChangedBy(1, pageable))
                .thenReturn(new PageImpl<>(List.of(testLog), pageable, 1));
        when(auditHistoryRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testLog), pageable, 1));

        assertThat(service.getAuditHistoryByUser(1)).hasSize(1);
        assertThat(service.getAuditHistoryByDateRange(
                LocalDateTime.now(), LocalDateTime.now().plusDays(1), 0, 50)).hasSize(1);
    }
}
