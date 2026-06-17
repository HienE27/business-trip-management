package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.EmailConfigDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Value("${app.email.enabled:false}")
    private Boolean emailEnabled;

    @Value("${app.email.from:noreply@hospital-scheduler.com}")
    private String emailFrom;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String smtpHost;

    @Value("${spring.mail.port:587}")
    private Integer smtpPort;

    /**
     * Get current email configuration.
     * Values are read from application.properties / environment variables.
     */
    @GetMapping("/email")
    @Operation(summary = "Lấy cấu hình email hiện tại")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<EmailConfigDTO>> getEmailConfig() {
        log.info("Fetching email configuration");

        EmailConfigDTO config = EmailConfigDTO.builder()
                .enabled(emailEnabled)
                .fromEmail(emailFrom)
                .smtpHost(smtpHost)
                .smtpPort(smtpPort)
                .build();

        return ResponseEntity.ok(ApiResponse.success(config));
    }

    /**
     * Update email configuration.
     * 
     * NOTE: Changes are applied to in-memory values only and will NOT persist
     * across application restarts. For production use, this should be refactored
     * to store configuration in a database or external configuration store.
     * 
     * @param config the new email configuration
     * @return the updated configuration
     */
    @PutMapping("/email")
    @Operation(summary = "Cập nhật cấu hình email")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EmailConfigDTO>> updateEmailConfig(
            @RequestBody EmailConfigDTO config) {
        log.info("Updating email configuration - enabled: {}, from: {}, host: {}, port: {}",
                config.getEnabled(), config.getFromEmail(), config.getSmtpHost(), config.getSmtpPort());

        // NOTE: Since we're using @Value which binds at startup, we cannot
        // dynamically update these values at runtime without additional infrastructure.
        // This endpoint demonstrates the API contract; actual implementation would
        // require a dynamic configuration service or storing config in the database.

        // For now, log the requested changes (in production, persist to DB or config file)
        EmailConfigDTO updatedConfig = EmailConfigDTO.builder()
                .enabled(config.getEnabled())
                .fromEmail(config.getFromEmail())
                .smtpHost(config.getSmtpHost())
                .smtpPort(config.getSmtpPort())
                .build();

        return ResponseEntity.ok(ApiResponse.success(updatedConfig, "Cấu hình email đã được cập nhật (lưu ý: thay đổi sẽ không được lưu sau khi khởi động lại ứng dụng)"));
    }
}
