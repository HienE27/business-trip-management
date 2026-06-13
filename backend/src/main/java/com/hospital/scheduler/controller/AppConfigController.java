package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.service.AppConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/app-config")
@RequiredArgsConstructor
@Tag(name = "App Config", description = "Cấu hình hệ thống")
public class AppConfigController {

    private final AppConfigService appConfigService;

    @GetMapping("/email")
    @Operation(summary = "Lấy cấu hình email")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> getEmailConfig() {
        Map<String, Boolean> config = Map.of(
                "emailEnabled", appConfigService.isEmailEnabled(),
                "conflictEmailEnabled", appConfigService.isConflictEmailEnabled()
        );
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PutMapping("/email")
    @Operation(summary = "Cập nhật cấu hình email")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> updateEmailConfig(
            @RequestParam boolean emailEnabled,
            @RequestParam boolean conflictEmailEnabled) {
        appConfigService.setEmailEnabled(emailEnabled);
        appConfigService.setConflictEmailEnabled(conflictEmailEnabled);
        Map<String, Boolean> config = Map.of(
                "emailEnabled", appConfigService.isEmailEnabled(),
                "conflictEmailEnabled", appConfigService.isConflictEmailEnabled()
        );
        return ResponseEntity.ok(ApiResponse.success(config));
    }
}
