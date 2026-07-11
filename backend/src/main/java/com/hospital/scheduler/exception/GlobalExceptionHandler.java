package com.hospital.scheduler.exception;

import com.hospital.scheduler.config.RequestIdFilter;
import com.hospital.scheduler.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.CannotAcquireLockException;
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
 * Global exception-to-HTTP-response mapper.
 *
 * Security/privacy contract: the catch-all {@link #handleGeneral(Exception, HttpServletRequest)}
 * handler MUST NOT echo {@code ex.getMessage()} — Hibernate/JDBC exception messages can
 * contain SQL fragments with parameter values (e.g. user-controlled IDs), which would
 * leak DB structure and possibly data to the client. We log the full message server-side
 * and return a generic "Internal server error" to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Domain exceptions (intentional throws from service layer) ─────────────

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
                        .message("Dữ liệu đầu vào không hợp lệ")
                        .data(errors)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    // ── HTTP layer / infrastructure exceptions ─────────────────────────────────

    /**
     * BUG-C2 fix: data-integrity violations must NOT leak SQL details, but the
     * client still needs to know WHICH kind of conflict to react sensibly
     * (refresh / drop duplicate / wait for lock / fix oversize input).
     *
     * We classify by walking the cause chain and matching well-known MySQL /
     * Postgres / Oracle signatures. Server-side log keeps the full message
     * (which may contain SQL fragments with bound parameter values) for ops.
     *
     * Categories:
     *   - FK / parent row referenced  → 409, "đang được tham chiếu"
     *   - Unique violation            → 409, "dữ liệu bị trùng"
     *   - Lock wait / deadlock        → 409, "đang bị khoá bởi thao tác khác"
     *   - Data too long / numeric     → 400, "vượt quá giới hạn cho phép"
     *   - Default (still 409)         → 409, generic "đang được sử dụng"
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        // Full message goes to server log only — never echo back to client.
        log.warn("Data integrity violation on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        // Collect signatures from the full cause chain (Hibernate wraps MySQL
        // exceptions up to 3–4 levels deep). Bound the walk to avoid infinite
        // loops on self-referential causes.
        StringBuilder chain = new StringBuilder();
        Throwable cursor = ex;
        int depth = 0;
        while (cursor != null && depth++ < 8) {
            String m = cursor.getMessage();
            if (m != null) {
                chain.append(m).append('\n');
            }
            if (cursor.getCause() == cursor) {
                break;
            }
            cursor = cursor.getCause();
        }
        String haystack = chain.toString().toLowerCase(java.util.Locale.ROOT);

        // 1. Lock wait timeout / deadlock — usually a transient conflict
        if (haystack.contains("lock wait timeout")
                || haystack.contains("deadlock")
                || haystack.contains("try restarting transaction")) {
            return errorResponse(HttpStatus.CONFLICT,
                    "Bản ghi đang bị khoá bởi thao tác khác, vui lòng thử lại sau vài giây");
        }

        // 2. Foreign-key violation (parent / child)
        if (haystack.contains("foreign key")
                || haystack.contains("ora-02292")
                || haystack.contains("cannot delete or update a parent row")
                || haystack.contains("violates foreign key")
                || haystack.contains("is still referenced")) {
            return errorResponse(HttpStatus.CONFLICT,
                    "Không thể xoá: bản ghi đang được tham chiếu bởi dữ liệu khác");
        }

        // 3. Unique-constraint violation — surface a short, safe hint.
        //    We deliberately do NOT include the index name or column list
        //    because those leak schema; we just say something is duplicated.
        if (haystack.contains("duplicate entry")
                || haystack.contains("ora-00001")
                || haystack.contains("unique constraint")
                || haystack.contains("violates unique")) {
            return errorResponse(HttpStatus.CONFLICT,
                    "Dữ liệu bị trùng — vui lòng tải lại trang và thử lại");
        }

        // 4. Truncation / out-of-range / type-mismatch on a value
        if (haystack.contains("data too long")
                || haystack.contains("value too long")
                || haystack.contains("ora-12899")
                || haystack.contains("out of range")
                || haystack.contains("incorrect ")
                || haystack.contains("truncated")
                || haystack.contains("string data, right truncation")) {
            return errorResponse(HttpStatus.BAD_REQUEST,
                    "Giá trị vượt quá giới hạn cho phép — vui lòng kiểm tra lại dữ liệu nhập");
        }

        // 5. Fallback: keep historical message verbatim so we don't break
        //    any caller (or test) that pattern-matches on it.
        return errorResponse(HttpStatus.CONFLICT,
                "Không thể thực hiện: dữ liệu đang được sử dụng ở nơi khác");
    }

    /**
     * BUG-M4 fix: Malformed JSON body (e.g. workDate: "not-a-date") must not
     * return HTTP 500 with Jackson stack trace.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        String rawMessage = ex.getMessage() != null ? ex.getMessage() : "";

        String hint = "Yêu cầu không hợp lệ: vui lòng kiểm tra định dạng dữ liệu";
        // Check the top-level message for date parsing errors.
        if (rawMessage.contains("LocalDate") || rawMessage.contains("DateTimeParse")
                || rawMessage.contains("ISO-8601")) {
            hint = "Định dạng ngày không hợp lệ. Sử dụng: YYYY-MM-DD";
        } else if (rawMessage.contains("Discriminator")) {
            hint = "Loại yêu cầu không hợp lệ";
        }

        return errorResponse(HttpStatus.BAD_REQUEST, hint);
    }

    /**
     * BUG-M3 fix: Non-numeric path variable (e.g. /periods/abc) must not return
     * HTTP 500 with type mismatch stack trace.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String paramName = ex.getName();
        String expectedType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        String message = String.format("Tham số '%s' có định dạng không đúng (cần %s)", paramName, expectedType);

        return errorResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * BUG-M1 fix: Unknown URL must return HTTP 404, not HTTP 500
     * "No static resource ...". Requires:
     *   spring.mvc.throw-exception-if-no-handler-found=true
     *   spring.web.resources.add-mappings=false
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoHandlerFound(
            NoHandlerFoundException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên: " + request.getRequestURI());
    }

    /**
     * Missing required query/path parameter.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        String message = String.format("Tham số '%s' là bắt buộc", ex.getParameterName());
        return errorResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * Wrong HTTP method (e.g. POST to a GET-only endpoint).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.METHOD_NOT_ALLOWED,
                "Phương thức " + ex.getMethod() + " không được hỗ trợ cho endpoint này");
    }

    /**
     * File upload exceeds configured size limit.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<?>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.PAYLOAD_TOO_LARGE,
                "Dung lượng file vượt quá giới hạn cho phép (tối đa 5MB)");
    }

    /**
     * Optimistic lock failure — concurrent edit conflict.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<?>> handleOptimisticLocking(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.CONFLICT,
                "Dữ liệu đã được chỉnh sửa bởi người khác. Vui lòng tải lại trang và thử lại");
    }

    /**
     * Pessimistic / row lock wait timeout (e.g. concurrent transactions on the
     * same row in algorithm_config hit {@code innodb_lock_wait_timeout}). The
     * previous catch-all returned 500 "lỗi nội bộ" which obscured the real cause;
     * surface a clear, actionable hint so the client can retry.
     */
    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ApiResponse<?>> handleCannotAcquireLock(
            CannotAcquireLockException ex, HttpServletRequest request) {
        log.warn("Lock wait timeout on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return errorResponse(HttpStatus.CONFLICT,
                "Bản ghi đang bị khoá bởi thao tác khác, vui lòng thử lại sau vài giây");
    }

    // ── Catch-all (SECURITY: never echo ex.getMessage() to client) ─────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneral(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        // SECURITY: Return generic message only — ex.getMessage() may contain SQL
        // fragments, stack traces, or internal paths that must not reach the client.
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Đã xảy ra lỗi nội bộ. Vui lòng thử lại sau hoặc liên hệ quản trị viên");
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private static ResponseEntity<ApiResponse<?>> errorResponse(HttpStatus status, String message) {
        return ResponseEntity
                .status(status)
                .body(ApiResponse.<Object>builder()
                        .success(false)
                        .message(message)
                        .data(MDC.get(RequestIdFilter.MDC_KEY)) // expose requestId to client for support correlation
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}
