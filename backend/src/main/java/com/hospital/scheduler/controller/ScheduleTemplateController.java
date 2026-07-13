package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.ScheduleTemplateRequest;
import com.hospital.scheduler.dto.response.ScheduleTemplateResponse;
import com.hospital.scheduler.dto.response.TemplatePreviewItem;
import com.hospital.scheduler.security.Permissions;
import com.hospital.scheduler.service.ScheduleTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/schedule-templates")
@RequiredArgsConstructor
@Tag(name = "Schedule Template", description = "Quản lý mẫu lịch trực")
public class ScheduleTemplateController {

    private final ScheduleTemplateService templateService;

    @GetMapping
    @Operation(summary = "Lấy danh sách mẫu lịch")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_MANAGE + "') or hasAuthority('" + Permissions.AUTO_SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<List<ScheduleTemplateResponse>>> getAllTemplates() {
        return ResponseEntity.ok(ApiResponse.success(templateService.getAllTemplates()));
    }

    @GetMapping("/active")
    @Operation(summary = "Lấy danh sách mẫu lịch đang hoạt động")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_MANAGE + "') or hasAuthority('" + Permissions.AUTO_SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<List<ScheduleTemplateResponse>>> getActiveTemplates() {
        return ResponseEntity.ok(ApiResponse.success(templateService.getActiveTemplates()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết mẫu lịch")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_MANAGE + "') or hasAuthority('" + Permissions.AUTO_SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<ScheduleTemplateResponse>> getTemplateById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(templateService.getTemplateById(id)));
    }

    @PostMapping
    @Operation(summary = "Tạo mẫu lịch mới")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_MANAGE + "')")
    public ResponseEntity<ApiResponse<ScheduleTemplateResponse>> createTemplate(
            @Valid @RequestBody ScheduleTemplateRequest request) {
        ScheduleTemplateResponse created = templateService.createTemplate(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Tạo mẫu lịch thành công"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật mẫu lịch")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_MANAGE + "')")
    public ResponseEntity<ApiResponse<ScheduleTemplateResponse>> updateTemplate(
            @PathVariable Integer id,
            @Valid @RequestBody ScheduleTemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(templateService.updateTemplate(id, request), "Cập nhật mẫu lịch thành công"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa mẫu lịch (soft delete)")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_MANAGE + "')")
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(@PathVariable Integer id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa mẫu lịch thành công"));
    }

    @PostMapping("/{templateId}/apply/{periodId}")
    @Operation(summary = "Áp dụng mẫu lịch vào kỳ lịch")
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_APPLY + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> applyTemplate(
            @PathVariable Integer templateId,
            @PathVariable Integer periodId) {
        int count = templateService.applyTemplateToPeriod(templateId, periodId);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("templateId", templateId, "periodId", periodId, "appliedCount", count),
                "Áp dụng mẫu lịch thành công, đã tạo " + count + " yêu cầu nhân sự"));
    }

    @GetMapping("/{templateId}/preview/{periodId}")
    @Operation(summary = "Xem trước mẫu lịch trước khi áp dụng — trả về danh sách ca dự kiến")
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<List<TemplatePreviewItem>>> previewTemplate(
            @PathVariable Integer templateId,
            @PathVariable Integer periodId) {
        List<TemplatePreviewItem> preview = templateService.previewTemplate(templateId, periodId);
        return ResponseEntity.ok(ApiResponse.success(preview, "Xem trước mẫu lịch thành công"));
    }

    @PostMapping("/{templateId}/apply/{periodId}/with-edits")
    @Operation(summary = "Áp dụng mẫu lịch GENERATED vào kỳ lịch với các chỉnh sửa của người dùng")
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_APPLY + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> applyTemplateWithEdits(
            @PathVariable Integer templateId,
            @PathVariable Integer periodId,
            @Valid @RequestBody com.hospital.scheduler.dto.request.TemplateApplyWithEditsRequest request) {
        request.setTemplateId(templateId);
        request.setPeriodId(periodId);
        int count = templateService.applyTemplateWithEdits(request);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("templateId", templateId, "periodId", periodId, "appliedCount", count),
                "Áp dụng mẫu lịch với chỉnh sửa thành công, đã tạo " + count + " lịch trực"));
    }
}