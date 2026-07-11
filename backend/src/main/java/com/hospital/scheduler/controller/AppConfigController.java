package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.EmailConfigDTO;
import com.hospital.scheduler.service.AppConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for application configuration endpoints.
 * 
 * NOTE: This controller reads/writes email configuration using Spring's @Value annotation.
 * Values are read from application.properties (or environment variables in production).
 * Changes made via PUT endpoint will NOT persist across application restarts.
 * In a production environment, this configuration should be stored in a database
 * or a configuration file that supports hot-reloading.
 */
@RestController
@RequestMapping("/api/v1/app-config")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "App Config", description = "Cấu hình ứng dụng")
public class AppConfigController {

    private final AppConfigService appConfigService;

    @Value("${app.email.from:noreply@hospital-scheduler.com}")
    private String emailFrom;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String smtpHost;

    @Value("${spring.mail.port:587}")
    private Integer smtpPort;

    @GetMapping("/email")
    @Operation(summary = "Lấy cấu hình email hiện tại")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<EmailConfigDTO>> getEmailConfig() {
        log.info("Fetching email configuration");

        EmailConfigDTO config = EmailConfigDTO.builder()
                .emailEnabled(appConfigService.isEmailEnabled())
                .conflictEmailEnabled(appConfigService.isConflictEmailEnabled())
                .fromEmail(emailFrom)
                .smtpHost(smtpHost)
                .smtpPort(smtpPort)
                .build();

        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PutMapping("/email")
    @Operation(summary = "Cập nhật cấu hình email")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EmailConfigDTO>> updateEmailConfig(
            @Valid @RequestBody EmailConfigDTO config) {
        log.info("Updating email configuration - enabled: {}, conflictEnabled: {}, from: {}, host: {}, port: {}",
                config.getEmailEnabled(), config.getConflictEmailEnabled(),
                config.getFromEmail(), config.getSmtpHost(), config.getSmtpPort());

        if (config.getEmailEnabled() != null) {
            appConfigService.setEmailEnabled(config.getEmailEnabled());
        }
        if (config.getConflictEmailEnabled() != null) {
            appConfigService.setConflictEmailEnabled(config.getConflictEmailEnabled());
        }

        EmailConfigDTO updatedConfig = EmailConfigDTO.builder()
                .emailEnabled(appConfigService.isEmailEnabled())
                .conflictEmailEnabled(appConfigService.isConflictEmailEnabled())
                .fromEmail(emailFrom)
                .smtpHost(smtpHost)
                .smtpPort(smtpPort)
                .build();

        return ResponseEntity.ok(ApiResponse.success(updatedConfig, "Cấu hình email đã được cập nhật"));
    }
}
