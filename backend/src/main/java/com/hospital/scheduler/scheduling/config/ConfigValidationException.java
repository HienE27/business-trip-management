package com.hospital.scheduler.scheduling.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Exception thrown when config validation fails.
 */
public class ConfigValidationException extends RuntimeException {

    private final ConfigValidator.ValidationResult result;

    public ConfigValidationException(ConfigValidator.ValidationResult result) {
        super(buildMessage(result));
        this.result = result;
    }

    public ConfigValidationException(String message) {
        super(message);
        this.result = null;
    }

    public ConfigValidator.ValidationResult getResult() {
        return result;
    }

    public boolean hasErrors() {
        return result != null && result.hasErrors();
    }

    public boolean hasWarnings() {
        return result != null && result.hasWarnings();
    }

    private static String buildMessage(ConfigValidator.ValidationResult result) {
        if (result == null) return "Configuration validation failed";
        long errorCount = result.errors().size();
        long warningCount = result.warnings().size();
        return String.format("Config validation failed: %d error(s), %d warning(s)",
                errorCount, warningCount);
    }

    /**
     * REST response DTO for validation results.
     */
    public record ValidationResponse(
            boolean valid,
            int errorCount,
            int warningCount,
            int infoCount,
            java.util.List<ViolationDto> errors,
            java.util.List<ViolationDto> warnings,
            java.util.List<ViolationDto> infos
    ) {
        public record ViolationDto(
                String fieldPath,
                String message,
                String severity
        ) {}
    }

    public ValidationResponse toResponse() {
        if (result == null) {
            return new ValidationResponse(false, 1, 0, 0,
                    java.util.List.of(new ValidationResponse.ViolationDto(null, getMessage(), "ERROR")),
                    java.util.List.of(), java.util.List.of());
        }
        return new ValidationResponse(
                !result.hasErrors(),
                result.errors().size(),
                result.warnings().size(),
                result.infos().size(),
                result.errors().stream().map(v ->
                        new ValidationResponse.ViolationDto(v.fieldPath(), v.messageVi(), v.severity().name())).toList(),
                result.warnings().stream().map(v ->
                        new ValidationResponse.ViolationDto(v.fieldPath(), v.messageVi(), v.severity().name())).toList(),
                result.infos().stream().map(v ->
                        new ValidationResponse.ViolationDto(v.fieldPath(), v.messageVi(), v.severity().name())).toList()
        );
    }
}
