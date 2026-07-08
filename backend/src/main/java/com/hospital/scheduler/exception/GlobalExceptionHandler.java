package com.hospital.scheduler.exception;

import com.hospital.scheduler.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps server-side exceptions to clean HTTP responses with Vietnamese messages.
 *
 * Security/privacy note: the catch-all {@link #handleGeneral(Exception, HttpServletRequest)}
 * handler MUST NOT echo {@code ex.getMessage()} — Hibernate/JDBC exception messages can
 * contain SQL fragments with parameter values (e.g. user-controlled IDs), which would
 * leak DB structure and possibly data to the client. We log the full message server-side
 * and return a generic "Internal server error" to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Domain exceptions (intentional throws) ───────────────────────────────

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAuthorizationDenied(
            AuthorizationDeniedException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập tài nguyên này");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<?>> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.UNAUTHORIZED, "Xác thực thất bại");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<?>> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        // Generic message — don't reveal whether username or password was wrong
        return errorResponse(HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không đúng");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<?>> handleBadRequest(
            BadRequestException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<?>> handleConflict(
            ConflictException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiResponse<?>> handleForbiddenOperation(
            ForbiddenOperationException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // ── Validation / serialization / deserialization ────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return ResponseEntity.badRequest()
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Input validation failed")
                        .data(errors)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        // Triggered by: null body, empty body, malformed JSON, malformed date,
        // wrong type in JSON field, etc. We don't echo ex.getMessage() because
        // it can leak the expected format and field name.
        log.warn("Malformed request body on {}: {}", request.getRequestURI(), ex.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, "Request body không hợp lệ hoặc thiếu");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        // Triggered by e.g. GET /periods/abc where {id} is Integer.
        // Don't leak the parameter name with the raw value — just say "invalid".
        log.warn("Type mismatch on {}: param={}, value={}",
                request.getRequestURI(), ex.getName(), ex.getValue());
        return errorResponse(HttpStatus.BAD_REQUEST,
                "Giá trị tham số không hợp lệ: " + ex.getName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.BAD_REQUEST,
                "Thiếu tham số bắt buộc: " + ex.getParameterName());
    }

    // ── Routing / HTTP method ───────────────────────────────────────────────

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoHandler(
            NoHandlerFoundException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.NOT_FOUND, "Endpoint không tồn tại: " + ex.getRequestURL());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.METHOD_NOT_ALLOWED,
                "Phương thức HTTP không được hỗ trợ: " + ex.getMethod());
    }

    // ── Database / data integrity ───────────────────────────────────────────

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        // BUG-C2 fix: never expose the SQL/constraint message. Log server-side, return generic.
        log.warn("Data integrity violation on {}: {}",
                request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return errorResponse(HttpStatus.CONFLICT,
                "Không thể thực hiện thao tác do dữ liệu liên quan (ràng buộc khóa ngoại hoặc duy nhất). "
                        + "Vui lòng kiểm tra và xóa dữ liệu liên quan trước.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<?>> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.CONFLICT,
                "Dữ liệu đã bị thay đổi bởi người khác. Vui lòng tải lại và thử lại.");
    }

    // ── File upload ─────────────────────────────────────────────────────────

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<?>> handleUploadTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.PAYLOAD_TOO_LARGE,
                "File tải lên vượt quá kích thước cho phép");
    }

    // ── Catch-all (last resort) ─────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneral(
            Exception ex, HttpServletRequest request) {
        // BUG-C2 fix: NEVER return ex.getMessage() — it may contain SQL fragments,
        // file paths, or class names. Log full stack server-side, return generic.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau hoặc liên hệ quản trị viên.");
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private static ResponseEntity<ApiResponse<?>> errorResponse(HttpStatus status, String message) {
        return ResponseEntity
                .status(status)
                .body(ApiResponse.<Object>builder()
                        .success(false)
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}