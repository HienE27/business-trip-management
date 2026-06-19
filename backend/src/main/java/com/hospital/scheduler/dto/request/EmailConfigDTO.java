package com.hospital.scheduler.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Email configuration for the application")
public class EmailConfigDTO {

    @Schema(description = "Whether email notifications are enabled", example = "false")
    private Boolean emailEnabled;

    @Schema(description = "Whether conflict email alerts are enabled", example = "false")
    private Boolean conflictEmailEnabled;

    @Schema(description = "The from email address", example = "noreply@hospital-scheduler.com")
    @Email(message = "Email không hợp lệ")
    private String fromEmail;

    @Schema(description = "SMTP server hostname", example = "smtp.gmail.com")
    @NotBlank(message = "SMTP host không được để trống")
    private String smtpHost;

    @Schema(description = "SMTP server port", example = "587")
    @NotNull(message = "SMTP port không được để trống")
    @Min(value = 1, message = "SMTP port phải lớn hơn 0")
    @Max(value = 65535, message = "SMTP port phải nhỏ hơn 65536")
    private Integer smtpPort;
}
