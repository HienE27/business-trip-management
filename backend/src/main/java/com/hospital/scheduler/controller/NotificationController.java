package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.NotificationDTO;
import com.hospital.scheduler.dto.response.NotificationResponse;
import com.hospital.scheduler.entity.Notification;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.security.AuthContextService;
import com.hospital.scheduler.security.Permissions;
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

    private void validateNotificationOwnership(Integer notificationId, String username) {
        Notification notification = notificationService.findById(notificationId);
        if (notification == null || !notification.getStaff().getUsername().equals(username)) {
            throw new ResourceNotFoundException("Không tìm thấy thông báo");
        }
    }

    private static final int MAX_PAGE_SIZE = 200;

    /**
     * Sanitize a pagination size param to a safe range so a malicious or
     * buggy client can't ask for {@code size=Integer.MAX_VALUE} and OOM the
     * DB or the JSON serializer.
     *
     * <p>BUGFIX (was BE#17 / BE#18) helper. Used by every paginated endpoint
     * below.
     */
    private static int clampPageSize(int requested) {
        if (requested <= 0) return 20; // default
        if (requested > MAX_PAGE_SIZE) return MAX_PAGE_SIZE;
        return requested;
    }

    @GetMapping("/staff/{staffId}")
    @Operation(summary = "Lấy thông báo theo nhân sự (kế thừa — unbounded, dùng /paginated)")
    @PreAuthorize("hasAuthority('" + Permissions.NOTIFICATION_VIEW + "') or @authContextService.isCurrentStaff(#staffId)")
    @Deprecated
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getByStaff(
            @PathVariable Integer staffId,
            @RequestParam(defaultValue = "100") int limit) {
        // BUGFIX (was BE#17): the previous version returned the entire notification
        // list with no upper bound — a user with thousands of notifications would
        // block both DB and JSON serialization. We now accept a {@code limit}
        // query param (default 100, capped at MAX_PAGE_SIZE) so the endpoint
        // stays usable as a quick-glance view. Callers needing deeper history
        // should use {@code /staff/{id}/paginated}. The endpoint is also marked
        // {@link Deprecated} so frontend migration to paginated is encouraged.
        int capped = clampPageSize(limit);
        List<NotificationResponse> page = notificationService.getNotificationsByStaff(staffId).stream()
                .limit(capped)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/staff/{staffId}/paginated")
    @Operation(summary = "Lấy thông báo theo nhân sự (có phân trang)")
    @PreAuthorize("hasAuthority('" + Permissions.NOTIFICATION_VIEW + "') or @authContextService.isCurrentStaff(#staffId)")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getByStaffPaginated(
            @PathVariable Integer staffId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getNotificationsByStaffPaginated(staffId, page, clampPageSize(size))));
    }

    @GetMapping("/me/page")
    @Operation(summary = "Lấy thông báo của tôi (phân trang + filter tab)")
    @PreAuthorize("hasAuthority('" + Permissions.NOTIFICATION_MANAGE_SELF + "')")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getMyNotificationsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String tab) {
        Integer staffId = authContextService.getCurrentStaff().getId();
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getNotificationsByStaffPaginated(staffId, page, clampPageSize(size), tab)));
    }

    @GetMapping("/staff/{staffId}/unread")
    @Operation(summary = "Lấy thông báo chưa đọc (kế thừa — unbounded, dùng /paginated)")
    @PreAuthorize("hasAuthority('" + Permissions.NOTIFICATION_VIEW + "') or @authContextService.isCurrentStaff(#staffId)")
    @Deprecated
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUnread(
            @PathVariable Integer staffId,
            @RequestParam(defaultValue = "100") int limit) {
        // BUGFIX (was BE#17): same OOM protection as getByStaff — accept a
        // {@code limit} param capped at MAX_PAGE_SIZE.
        int capped = clampPageSize(limit);
        List<NotificationResponse> page = notificationService.getUnreadNotifications(staffId).stream()
                .limit(capped)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/staff/{staffId}/unread/count")
    @Operation(summary = "Đếm thông báo chưa đọc")
    @PreAuthorize("hasAuthority('" + Permissions.NOTIFICATION_VIEW + "') or @authContextService.isCurrentStaff(#staffId)")
    public ResponseEntity<ApiResponse<Map<String, Long>>> countUnread(@PathVariable Integer staffId) {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("count", notificationService.countUnreadNotifications(staffId))));
    }

    @GetMapping("/me/unread/count")
    @Operation(summary = "Đếm thông báo chưa đọc của tôi (toàn DB, không phụ thuộc trang)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Long>>> countMyUnread() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("count", notificationService.getMyUnreadCount())));
    }

    @PostMapping
    @Operation(summary = "Tạo thông báo cho nhân sự")
    @PreAuthorize("hasAuthority('" + Permissions.NOTIFICATION_CREATE + "')")
    public ResponseEntity<ApiResponse<NotificationResponse>> create(
            @Valid @RequestBody NotificationDTO dto) {
        NotificationResponse created = notificationService.createNotification(dto.getRecipientId(), dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Tạo thông báo thành công"));
    }

    @PostMapping("/broadcast")
    @Operation(summary = "Gửi thông báo cho tất cả nhân sự")
    @PreAuthorize("hasAuthority('" + Permissions.NOTIFICATION_BROADCAST + "')")
    public ResponseEntity<ApiResponse<Map<String, String>>> broadcast(@Valid @RequestBody NotificationDTO dto) {
        notificationService.createNotificationForAllStaff(dto.getTitle(), dto.getMessage());
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("status", "broadcast_sent"), "Đã gửi thông báo cho tất cả nhân sự"));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Đánh dấu đã đọc")
    @PreAuthorize("hasAuthority('" + Permissions.NOTIFICATION_VIEW + "') or (hasAuthority('" + Permissions.NOTIFICATION_MANAGE_SELF + "') and @authContextService.isCurrentStaffOwner(#id))")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.markAsRead(id), "Đã đánh dấu là đã đọc"));
    }

    @PutMapping("/staff/{staffId}/read-all")
    @Operation(summary = "Đánh dấu tất cả là đã đọc")
    @PreAuthorize("hasAuthority('" + Permissions.NOTIFICATION_VIEW + "') or @authContextService.isCurrentStaff(#staffId)")
    public ResponseEntity<ApiResponse<Map<String, String>>> markAllAsRead(@PathVariable Integer staffId) {
        notificationService.markAllAsRead(staffId);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("status", "all_read"), "Đã đánh dấu tất cả là đã đọc"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa thông báo")
    @PreAuthorize("hasAuthority('" + Permissions.NOTIFICATION_VIEW + "') or (hasAuthority('" + Permissions.NOTIFICATION_MANAGE_SELF + "') and @authContextService.isCurrentStaffOwner(#id))")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa thông báo."));
    }
}