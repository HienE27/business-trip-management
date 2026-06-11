package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.NotificationDTO;
import com.hospital.scheduler.dto.response.NotificationResponse;
import com.hospital.scheduler.security.AuthContextService;
import com.hospital.scheduler.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "Quản lý thông báo")
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthContextService authContextService;

    @GetMapping("/staff/{staffId}")
    @Operation(summary = "Lấy thông báo theo nhân sự")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or @authContextService.isCurrentStaff(#staffId)")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getByStaff(@PathVariable Integer staffId) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getNotificationsByStaff(staffId)));
    }

    @GetMapping("/staff/{staffId}/paginated")
    @Operation(summary = "Lấy thông báo theo nhân sự (có phân trang)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or @authContextService.isCurrentStaff(#staffId)")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getByStaffPaginated(
            @PathVariable Integer staffId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getNotificationsByStaffPaginated(staffId, page, size)));
    }

    @GetMapping("/staff/{staffId}/unread")
    @Operation(summary = "Lấy thông báo chưa đọc")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or @authContextService.isCurrentStaff(#staffId)")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUnread(@PathVariable Integer staffId) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getUnreadNotifications(staffId)));
    }

    @GetMapping("/staff/{staffId}/unread/count")
    @Operation(summary = "Đếm thông báo chưa đọc")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or @authContextService.isCurrentStaff(#staffId)")
    public ResponseEntity<ApiResponse<Map<String, Long>>> countUnread(@PathVariable Integer staffId) {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("count", notificationService.countUnreadNotifications(staffId))));
    }

    @PostMapping("/staff/{staffId}")
    @Operation(summary = "Tạo thông báo cho nhân sự")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<NotificationResponse>> create(
            @PathVariable Integer staffId,
            @Valid @RequestBody NotificationDTO dto) {
        NotificationResponse created = notificationService.createNotification(staffId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Tạo thông báo thành công"));
    }

    @PostMapping("/broadcast")
    @Operation(summary = "Gửi thông báo cho tất cả nhân sự")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> broadcast(@Valid @RequestBody NotificationDTO dto) {
        notificationService.createNotificationForAllStaff(dto.getTitle(), dto.getMessage());
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("status", "broadcast_sent"), "Đã gửi thông báo cho tất cả nhân sự"));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Đánh dấu đã đọc")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.markAsRead(id), "Đã đánh dấu là đã đọc"));
    }

    @PutMapping("/staff/{staffId}/read-all")
    @Operation(summary = "Đánh dấu tất cả là đã đọc")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or @authContextService.isCurrentStaff(#staffId)")
    public ResponseEntity<ApiResponse<Map<String, String>>> markAllAsRead(@PathVariable Integer staffId) {
        notificationService.markAllAsRead(staffId);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("status", "all_read"), "Đã đánh dấu tất cả là đã đọc"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa thông báo")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa thông báo thành công"));
    }
}
