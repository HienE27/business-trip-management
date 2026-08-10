package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.HolidayRequest;
import com.hospital.scheduler.dto.response.HolidayResponse;
import com.hospital.scheduler.security.Permissions;
import com.hospital.scheduler.service.HolidayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/holidays")
@RequiredArgsConstructor
@Tag(name = "Holiday", description = "Quản lý ngày lễ")
public class HolidayController {

    private final HolidayService holidayService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả ngày lễ")
    @PreAuthorize("hasAuthority('" + Permissions.HOLIDAY_VIEW + "')")
    public ResponseEntity<ApiResponse<List<HolidayResponse>>> getAllHolidays() {
        return ResponseEntity.ok(ApiResponse.success(holidayService.getAllHolidays()));
    }

    @GetMapping("/page")
    @Operation(summary = "Lấy danh sách ngày lễ có phân trang, hỗ trợ lọc theo năm và trạng thái hoạt động")
    @PreAuthorize("hasAuthority('" + Permissions.HOLIDAY_VIEW + "')")
    public ResponseEntity<ApiResponse<Page<HolidayResponse>>> getHolidaysPage(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Boolean isActive,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(holidayService.getHolidaysPage(year, isActive, pageable)));
    }

    @GetMapping("/active")
    @Operation(summary = "Lấy danh sách ngày lễ đang hoạt động")
    @PreAuthorize("hasAuthority('" + Permissions.HOLIDAY_VIEW + "')")
    public ResponseEntity<ApiResponse<List<HolidayResponse>>> getActiveHolidays() {
        return ResponseEntity.ok(ApiResponse.success(holidayService.getActiveHolidays()));
    }

    @GetMapping("/year/{year}")
    @Operation(summary = "Lấy danh sách ngày lễ theo năm")
    @PreAuthorize("hasAuthority('" + Permissions.HOLIDAY_VIEW + "')")
    public ResponseEntity<ApiResponse<List<HolidayResponse>>> getHolidaysByYear(@PathVariable Integer year) {
        return ResponseEntity.ok(ApiResponse.success(holidayService.getHolidaysByYear(year)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết ngày lễ")
    @PreAuthorize("hasAuthority('" + Permissions.HOLIDAY_VIEW + "')")
    public ResponseEntity<ApiResponse<HolidayResponse>> getHolidayById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(holidayService.getHolidayById(id)));
    }

    @PostMapping
    @Operation(summary = "Tạo ngày lễ mới")
    @PreAuthorize("hasAuthority('" + Permissions.HOLIDAY_CREATE + "')")
    public ResponseEntity<ApiResponse<HolidayResponse>> createHoliday(@Valid @RequestBody HolidayRequest request) {
        HolidayResponse created = holidayService.createHoliday(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, "Tạo ngày lễ thành công"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật ngày lễ")
    @PreAuthorize("hasAuthority('" + Permissions.HOLIDAY_UPDATE + "')")
    public ResponseEntity<ApiResponse<HolidayResponse>> updateHoliday(
            @PathVariable Integer id,
            @Valid @RequestBody HolidayRequest request) {
        return ResponseEntity.ok(ApiResponse.success(holidayService.updateHoliday(id, request), "Cập nhật ngày lễ thành công"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa ngày lễ (soft delete)")
    @PreAuthorize("hasAuthority('" + Permissions.HOLIDAY_DELETE + "')")
    public ResponseEntity<ApiResponse<Void>> deleteHoliday(@PathVariable Integer id) {
        holidayService.deleteHoliday(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa ngày lễ thành công"));
    }
}
