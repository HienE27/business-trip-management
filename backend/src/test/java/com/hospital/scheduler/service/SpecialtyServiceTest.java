package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.SpecialtyRequest;
import com.hospital.scheduler.dto.response.SpecialtyResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.SpecialtyRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SpecialtyService Tests - CRUD chuyên khoa với audit + cache evict")
class SpecialtyServiceTest {

    @Mock private SpecialtyRepository specialtyRepository;
    @Mock private AuditHistoryService auditHistoryService;
    @Mock private AuthContextService authContextService;

    @InjectMocks private SpecialtyService service;

    private Staff currentStaff;

    @BeforeEach
    void setUp() {
        currentStaff = Staff.builder()
                .id(1).username("admin").fullName("Admin").isActive(true).build();
        currentStaff.setStaffRoles(new java.util.HashSet<>());

        when(authContextService.getCurrentStaff()).thenReturn(currentStaff);
    }

    private Specialty buildSpecialty(Integer id, String name, String desc, Boolean active) {
        Specialty s = Specialty.builder()
                .id(id).name(name).description(desc)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        s.setIsActive(active);
        return s;
    }

    @Test
    @DisplayName("getAllSpecialties -> map sang SpecialtyResponse")
    void getAll_specialties() {
        Specialty s1 = buildSpecialty(1, "Cardiology", "Tim mạch", true);
        Specialty s2 = buildSpecialty(2, "Pediatrics", "Nhi khoa", true);
        when(specialtyRepository.findAll()).thenReturn(List.of(s1, s2));

        List<SpecialtyResponse> result = service.getAllSpecialties();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Cardiology");
        assertThat(result.get(1).getName()).isEqualTo("Pediatrics");
    }

    @Test
    @DisplayName("getActiveSpecialties -> chỉ trả về isActive=true")
    void getActiveSpecialties() {
        Specialty active = buildSpecialty(1, "A", "desc", true);
        when(specialtyRepository.findByIsActiveTrue()).thenReturn(List.of(active));

        List<SpecialtyResponse> result = service.getActiveSpecialties();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsActive()).isTrue();
    }

    @Test
    @DisplayName("getSpecialtyById tồn tại -> trả response")
    void getById_found() {
        Specialty s = buildSpecialty(5, "Dermatology", "Da liễu", true);
        when(specialtyRepository.findById(5)).thenReturn(Optional.of(s));

        SpecialtyResponse result = service.getSpecialtyById(5);

        assertThat(result.getId()).isEqualTo(5);
        assertThat(result.getName()).isEqualTo("Dermatology");
    }

    @Test
    @DisplayName("getSpecialtyById không tồn tại -> ném ResourceNotFoundException")
    void getById_notFound() {
        when(specialtyRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSpecialtyById(999))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("createSpecialty với name trùng -> ném ConflictException")
    void create_duplicate() {
        SpecialtyRequest req = SpecialtyRequest.builder()
                .name("Cardiology").description("Tim mạch").build();
        when(specialtyRepository.findByName("Cardiology"))
                .thenReturn(Optional.of(buildSpecialty(1, "Cardiology", "", true)));

        assertThatThrownBy(() -> service.createSpecialty(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cardiology");

        verify(specialtyRepository, never()).save(any());
        verify(auditHistoryService, never()).logAction(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("createSpecialty thành công -> save + audit INSERT")
    void create_success() {
        SpecialtyRequest req = SpecialtyRequest.builder()
                .name("Neurology").description("Thần kinh").build();
        when(specialtyRepository.findByName("Neurology")).thenReturn(Optional.empty());
        when(specialtyRepository.save(any(Specialty.class)))
                .thenAnswer(inv -> {
                    Specialty s = inv.getArgument(0);
                    s.setId(10);
                    return s;
                });

        SpecialtyResponse result = service.createSpecialty(req);

        assertThat(result.getId()).isEqualTo(10);
        assertThat(result.getName()).isEqualTo("Neurology");
        assertThat(result.getIsActive()).isTrue();

        ArgumentCaptor<Specialty> captor = ArgumentCaptor.forClass(Specialty.class);
        verify(specialtyRepository).save(captor.capture());
        assertThat(captor.getValue().getIsActive()).isTrue();

        verify(auditHistoryService).logAction(
                eq("specialty"), eq(10), eq(AuditHistory.ActionType.INSERT),
                isNull(), any(Specialty.class), eq(1));
    }

    @Test
    @DisplayName("updateSpecialty đổi tên sang tên đã tồn tại ở specialty khác -> ConflictException")
    void update_nameConflictWithOther() {
        Specialty existing = buildSpecialty(5, "Old Name", "desc", true);
        Specialty conflicting = buildSpecialty(6, "New Name", "other", true);

        SpecialtyRequest req = SpecialtyRequest.builder()
                .name("New Name").description("desc").build();
        when(specialtyRepository.findById(5)).thenReturn(Optional.of(existing));
        when(specialtyRepository.findByName("New Name")).thenReturn(Optional.of(conflicting));

        assertThatThrownBy(() -> service.updateSpecialty(5, req))
                .isInstanceOf(ConflictException.class);

        verify(specialtyRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateSpecialty giữ nguyên tên của chính nó -> không conflict")
    void update_keepOwnName() {
        Specialty existing = buildSpecialty(5, "Same Name", "old desc", true);
        SpecialtyRequest req = SpecialtyRequest.builder()
                .name("Same Name").description("new desc").build();
        when(specialtyRepository.findById(5)).thenReturn(Optional.of(existing));
        when(specialtyRepository.findByName("Same Name")).thenReturn(Optional.of(existing));
        when(specialtyRepository.save(any(Specialty.class))).thenAnswer(inv -> inv.getArgument(0));

        SpecialtyResponse result = service.updateSpecialty(5, req);

        assertThat(result.getDescription()).isEqualTo("new desc");
        verify(auditHistoryService).logAction(
                eq("specialty"), eq(5), eq(AuditHistory.ActionType.UPDATE),
                any(), any(), eq(1));
    }

    @Test
    @DisplayName("updateSpecialty không tìm thấy -> ResourceNotFoundException")
    void update_notFound() {
        when(specialtyRepository.findById(404)).thenReturn(Optional.empty());

        SpecialtyRequest req = SpecialtyRequest.builder().name("x").build();

        assertThatThrownBy(() -> service.updateSpecialty(404, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deleteSpecialty -> set isActive=false, soft delete + audit DELETE")
    void delete_softDelete() {
        Specialty existing = buildSpecialty(7, "X", "d", true);
        when(specialtyRepository.findById(7)).thenReturn(Optional.of(existing));
        when(specialtyRepository.save(any(Specialty.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteSpecialty(7);

        ArgumentCaptor<Specialty> captor = ArgumentCaptor.forClass(Specialty.class);
        verify(specialtyRepository).save(captor.capture());
        assertThat(captor.getValue().getIsActive()).isFalse();

        verify(auditHistoryService).logAction(
                eq("specialty"), eq(7), eq(AuditHistory.ActionType.DELETE),
                any(Specialty.class), isNull(), eq(1));
    }

    @Test
    @DisplayName("deleteSpecialty không tìm thấy -> ResourceNotFoundException")
    void delete_notFound() {
        when(specialtyRepository.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteSpecialty(404))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(specialtyRepository, never()).save(any());
    }

    @Test
    @DisplayName("getSpecialtiesPage -> map Page sang Page<SpecialtyResponse>")
    void getSpecialtiesPage_maps() {
        Pageable pageable = PageRequest.of(0, 10);
        Specialty s = buildSpecialty(2, "B", "d", true);
        Page<Specialty> page = new PageImpl<>(List.of(s), pageable, 1);

        when(specialtyRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<SpecialtyResponse> result = service.getSpecialtiesPage(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("B");
    }

    @Test
    @DisplayName("getStatusCounts -> đếm total/ACTIVE/INACTIVE đúng")
    void getStatusCounts() {
        when(specialtyRepository.findAll()).thenReturn(List.of(
                buildSpecialty(1, "A", "", true),
                buildSpecialty(2, "B", "", true),
                buildSpecialty(3, "C", "", false),
                buildSpecialty(4, "D", "", true)
        ));
        when(specialtyRepository.count()).thenReturn(4L);

        Map<String, Long> counts = service.getStatusCounts();

        assertThat(counts).containsEntry("total", 4L);
        assertThat(counts).containsEntry("ACTIVE", 3L);
        assertThat(counts).containsEntry("INACTIVE", 1L);
    }

    @Test
    @DisplayName("getStatusCounts với isActive null -> tính là INACTIVE")
    void getStatusCounts_nullActiveCountedInactive() {
        when(specialtyRepository.findAll()).thenReturn(List.of(
                buildSpecialty(1, "A", "", null),
                buildSpecialty(2, "B", "", true)
        ));
        when(specialtyRepository.count()).thenReturn(2L);

        Map<String, Long> counts = service.getStatusCounts();

        assertThat(counts).containsEntry("ACTIVE", 1L);
        assertThat(counts).containsEntry("INACTIVE", 1L);
    }
}