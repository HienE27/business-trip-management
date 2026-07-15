package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.ShiftTypeRequest;
import com.hospital.scheduler.dto.response.ShiftTypeResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.ShiftTypeRepository;
import com.hospital.scheduler.security.AuthContextService;
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

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ShiftTypeService Tests - CRUD loại ca với audit + cache evict")
class ShiftTypeServiceTest {

    @Mock private ShiftTypeRepository shiftTypeRepository;
    @Mock private AuditHistoryService auditHistoryService;
    @Mock private AuthContextService authContextService;

    @InjectMocks private ShiftTypeService service;

    private Staff currentStaff;

    @BeforeEach
    void setUp() {
        currentStaff = Staff.builder()
                .id(1).username("admin").fullName("Admin").isActive(true).build();
        currentStaff.setStaffRoles(new java.util.HashSet<>());
        when(authContextService.getCurrentStaff()).thenReturn(currentStaff);
    }

    private ShiftType buildShiftType(String id, String name, boolean overnight, Integer fatigue) {
        return ShiftType.builder()
                .id(id)
                .name(name)
                .description("desc")
                .startTime(LocalTime.of(7, 30))
                .endTime(LocalTime.of(17, 0))
                .isOvernight(overnight)
                .fatigueScore(fatigue)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("getAllShiftTypes -> trả list response đầy đủ field")
    void getAll() {
        when(shiftTypeRepository.findAll()).thenReturn(List.of(
                buildShiftType("L01", "Trực 24/24", true, 5),
                buildShiftType("L02", "Thông tầm", false, 2)
        ));

        List<ShiftTypeResponse> result = service.getAllShiftTypes();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("L01");
        assertThat(result.get(0).getIsOvernight()).isTrue();
        assertThat(result.get(0).getFatigueScore()).isEqualTo(5);
        assertThat(result.get(1).getId()).isEqualTo("L02");
        assertThat(result.get(1).getIsOvernight()).isFalse();
    }

    @Test
    @DisplayName("getActiveShiftTypes -> chỉ trả isActive=true")
    void getActive() {
        when(shiftTypeRepository.findByIsActiveTrue()).thenReturn(List.of(
                buildShiftType("L01", "Trực 24/24", true, 5)
        ));

        List<ShiftTypeResponse> result = service.getActiveShiftTypes();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsActive()).isTrue();
    }

    @Test
    @DisplayName("getShiftTypeById tồn tại -> trả response")
    void getById_found() {
        when(shiftTypeRepository.findById("L01"))
                .thenReturn(Optional.of(buildShiftType("L01", "Trực 24/24", true, 5)));

        ShiftTypeResponse result = service.getShiftTypeById("L01");

        assertThat(result.getId()).isEqualTo("L01");
        assertThat(result.getName()).isEqualTo("Trực 24/24");
    }

    @Test
    @DisplayName("getShiftTypeById không tồn tại -> ResourceNotFoundException")
    void getById_notFound() {
        when(shiftTypeRepository.findById("L99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getShiftTypeById("L99"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("L99");
    }

    @Test
    @DisplayName("createShiftType với id đã tồn tại -> ConflictException")
    void create_duplicateId() {
        ShiftTypeRequest req = ShiftTypeRequest.builder()
                .id("L01").name("Trực 24/24").build();
        when(shiftTypeRepository.existsById("L01")).thenReturn(true);

        assertThatThrownBy(() -> service.createShiftType(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("L01");

        verify(shiftTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("createShiftType với isOvernight/fatigueScore null -> dùng default (false/1)")
    void create_defaultsApplied() {
        ShiftTypeRequest req = ShiftTypeRequest.builder()
                .id("L05").name("Test").description("d")
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0))
                .isOvernight(null).fatigueScore(null)
                .build();
        when(shiftTypeRepository.existsById("L05")).thenReturn(false);
        when(shiftTypeRepository.save(any(ShiftType.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.createShiftType(req);

        ArgumentCaptor<ShiftType> captor = ArgumentCaptor.forClass(ShiftType.class);
        verify(shiftTypeRepository).save(captor.capture());
        ShiftType saved = captor.getValue();
        assertThat(saved.getIsOvernight()).isFalse();
        assertThat(saved.getFatigueScore()).isEqualTo(1);
        assertThat(saved.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("createShiftType thành công -> audit INSERT")
    void create_success() {
        ShiftTypeRequest req = ShiftTypeRequest.builder()
                .id("L01").name("Trực 24/24").description("d")
                .startTime(LocalTime.of(7, 30)).endTime(LocalTime.of(7, 30))
                .isOvernight(true).fatigueScore(5)
                .build();
        when(shiftTypeRepository.existsById("L01")).thenReturn(false);
        when(shiftTypeRepository.save(any(ShiftType.class))).thenAnswer(inv -> inv.getArgument(0));

        ShiftTypeResponse result = service.createShiftType(req);

        assertThat(result.getId()).isEqualTo("L01");
        assertThat(result.getIsOvernight()).isTrue();

        verify(auditHistoryService).logAction(
                eq("shift_type"), eq("L01"), eq(AuditHistory.ActionType.INSERT),
                isNull(), any(ShiftType.class), eq(1));
    }

    @Test
    @DisplayName("updateShiftType không tồn tại -> ResourceNotFoundException")
    void update_notFound() {
        when(shiftTypeRepository.findById("L99")).thenReturn(Optional.empty());

        ShiftTypeRequest req = ShiftTypeRequest.builder().id("L99").name("x").build();

        assertThatThrownBy(() -> service.updateShiftType("L99", req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateShiftType với isOvernight/fatigueScore null -> giữ nguyên giá trị cũ")
    void update_keepExistingWhenNull() {
        ShiftType existing = buildShiftType("L01", "Old", true, 5);
        ShiftTypeRequest req = ShiftTypeRequest.builder()
                .id("L01").name("New").description("d")
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0))
                .isOvernight(null).fatigueScore(null)
                .build();
        when(shiftTypeRepository.findById("L01")).thenReturn(Optional.of(existing));
        when(shiftTypeRepository.save(any(ShiftType.class))).thenAnswer(inv -> inv.getArgument(0));

        ShiftTypeResponse result = service.updateShiftType("L01", req);

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getIsOvernight()).isTrue();
        assertThat(result.getFatigueScore()).isEqualTo(5);
    }

    @Test
    @DisplayName("updateShiftType với isOvernight/fatigueScore cụ thể -> ghi đè")
    void update_overridesWithProvided() {
        ShiftType existing = buildShiftType("L02", "Old", true, 5);
        ShiftTypeRequest req = ShiftTypeRequest.builder()
                .id("L02").name("New").description("d")
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0))
                .isOvernight(false).fatigueScore(2)
                .build();
        when(shiftTypeRepository.findById("L02")).thenReturn(Optional.of(existing));
        when(shiftTypeRepository.save(any(ShiftType.class))).thenAnswer(inv -> inv.getArgument(0));

        ShiftTypeResponse result = service.updateShiftType("L02", req);

        assertThat(result.getIsOvernight()).isFalse();
        assertThat(result.getFatigueScore()).isEqualTo(2);

        verify(auditHistoryService).logAction(
                eq("shift_type"), eq("L02"), eq(AuditHistory.ActionType.UPDATE),
                any(ShiftType.class), any(ShiftType.class), eq(1));
    }

    @Test
    @DisplayName("deleteShiftType -> soft delete + audit DELETE")
    void delete_softDelete() {
        ShiftType existing = buildShiftType("L01", "X", true, 5);
        when(shiftTypeRepository.findById("L01")).thenReturn(Optional.of(existing));
        when(shiftTypeRepository.save(any(ShiftType.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteShiftType("L01");

        ArgumentCaptor<ShiftType> captor = ArgumentCaptor.forClass(ShiftType.class);
        verify(shiftTypeRepository).save(captor.capture());
        assertThat(captor.getValue().getIsActive()).isFalse();

        verify(auditHistoryService).logAction(
                eq("shift_type"), eq("L01"), eq(AuditHistory.ActionType.DELETE),
                any(ShiftType.class), isNull(), eq(1));
    }

    @Test
    @DisplayName("deleteShiftType không tồn tại -> ResourceNotFoundException")
    void delete_notFound() {
        when(shiftTypeRepository.findById("L99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteShiftType("L99"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(shiftTypeRepository, never()).save(any());
    }
}