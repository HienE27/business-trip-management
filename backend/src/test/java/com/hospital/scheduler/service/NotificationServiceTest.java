package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.NotificationDTO;
import com.hospital.scheduler.dto.response.NotificationResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.Notification;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.NotificationRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.security.AuthContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationService Tests - Quản lý thông báo")
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private AuditHistoryService auditHistoryService;
    @Mock private AuthContextService authContextService;
    @Mock private NotificationBroadcastService notificationBroadcastService;

    @InjectMocks
    private NotificationService notificationService;

    private Staff testStaff;
    private Notification testNotification;

    @BeforeEach
    void setUp() {
        testStaff = Staff.builder()
                .id(1).username("staff1").fullName("Nguyen Van A").isActive(true).build();
        testStaff.setStaffRoles(new java.util.HashSet<>());

        testNotification = Notification.builder()
                .id(100)
                .staff(testStaff)
                .title("Phân công lịch mới")
                .message("Bạn được phân công lịch L01 ngày 2026-06-15")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("createNotification - Tạo thông báo")
    class CreateNotification {
        @Test
        @DisplayName("Nhân sự tồn tại -> tạo thông báo thành công")
        void shouldCreate() {
            when(staffRepository.findById(1)).thenReturn(Optional.of(testStaff));
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(inv -> {
                        Notification n = inv.getArgument(0);
                        n.setId(100);
                        n.setCreatedAt(LocalDateTime.now());
                        return n;
                    });

            NotificationDTO dto = new NotificationDTO("Tiêu đề", "Nội dung");
            NotificationResponse result = notificationService.createNotification(1, dto);

            assertThat(result.getTitle()).isEqualTo("Tiêu đề");
            assertThat(result.getStaff().getId()).isEqualTo(1);
        }

        @Test
        @DisplayName("Nhân sự không tồn tại -> throw ResourceNotFoundException")
        void staffNotFound_shouldThrow() {
            when(staffRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.createNotification(999, new NotificationDTO("t", "m")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("markAsRead - Đánh dấu đã đọc")
    class MarkAsRead {
        @Test
        @DisplayName("Thông báo tồn tại -> set isRead=true + audit log")
        void shouldMarkAsRead() {
            when(notificationRepository.findById(100)).thenReturn(Optional.of(testNotification));
            when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
            when(authContextService.getCurrentStaff()).thenReturn(testStaff);

            NotificationResponse result = notificationService.markAsRead(100);

            assertThat(result.getIsRead()).isTrue();
            assertThat(result.getReadAt()).isNotNull();
            verify(auditHistoryService).logAction(eq("notification"), eq(100),
                    eq(AuditHistory.ActionType.UPDATE), any(), any(), eq(1));
        }

        @Test
        @DisplayName("Không tìm thấy thông báo -> throw")
        void notFound_shouldThrow() {
            when(notificationRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.markAsRead(999))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("markAllAsRead - Đánh dấu tất cả đã đọc")
    class MarkAllAsRead {
        @Test
        @DisplayName("Bulk update và audit log")
        void shouldMarkAllAndAudit() {
            when(notificationRepository.markAllAsReadBulk(1)).thenReturn(5);

            notificationService.markAllAsRead(1);

            ArgumentCaptor<java.util.Map<String, Object>> metaCaptor = ArgumentCaptor.forClass(java.util.Map.class);
            verify(auditHistoryService).logAction(eq("notification"), isNull(),
                    eq(AuditHistory.ActionType.UPDATE), isNull(), metaCaptor.capture(), isNull());
            assertThat(metaCaptor.getValue()).containsEntry("markAllRead", true);
            assertThat(metaCaptor.getValue()).containsEntry("staffId", 1);
            assertThat(metaCaptor.getValue()).containsEntry("count", 5);
        }
    }

    @Nested
    @DisplayName("Pagination - Phân trang")
    class Pagination {
        @Test
        @DisplayName("getNotificationsByStaff với page/size -> trả về danh sách có phân trang")
        void paginated() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Notification> page = new PageImpl<>(List.of(testNotification), pageable, 1);
            when(notificationRepository.findByStaffId(eq(1), any(Pageable.class))).thenReturn(page);

            var result = notificationService.getNotificationsByStaffPaginated(1, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getUnreadNotifications & counts")
    class ReadCount {
        @Test
        @DisplayName("Trả về unread + đếm")
        void shouldReturnUnread() {
            when(notificationRepository.findUnreadByStaffId(1)).thenReturn(List.of(testNotification));
            when(notificationRepository.countUnreadByStaffId(1)).thenReturn(1L);

            assertThat(notificationService.getUnreadNotifications(1)).hasSize(1);
            assertThat(notificationService.countUnreadNotifications(1)).isEqualTo(1L);
        }
    }
}
