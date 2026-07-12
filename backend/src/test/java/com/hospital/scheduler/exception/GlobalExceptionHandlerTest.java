package com.hospital.scheduler.exception;

import com.hospital.scheduler.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GlobalExceptionHandler.
 * Tests that each exception type maps to the correct HTTP status and message.
 *
 * Covers fixes for:
 * - BUG-C2: DataIntegrityViolationException → 409 (no SQL leakage)
 * - BUG-M1: NoHandlerFoundException → 404
 * - BUG-M2: HttpMessageNotReadableException (null body) → 400
 * - BUG-M3: MethodArgumentTypeMismatchException → 400
 * - BUG-M4: Malformed JSON date → 400
 */
@DisplayName("GlobalExceptionHandler - Exception to HTTP Response Mapping")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/test");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Domain exceptions (service-layer throws)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Domain exceptions (service layer)")
    class DomainExceptions {

        @Test
        @DisplayName("AuthorizationDeniedException → 403")
        void authorizationDenied_returns403() {
            ResponseEntity<ApiResponse<?>> resp = handler.handleAuthorizationDenied(
                    new AuthorizationDeniedException("Access denied"), request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getMessage()).contains("không có quyền");
        }

        @Test
        @DisplayName("AccessDeniedException → 403")
        void accessDenied_returns403() {
            ResponseEntity<ApiResponse<?>> resp = handler.handleAccessDenied(
                    new AccessDeniedException("Denied"), request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
        }

        @Test
        @DisplayName("ResourceNotFoundException → 404")
        void resourceNotFound_returns404() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Staff not found");
            ResponseEntity<ApiResponse<?>> resp = handler.handleResourceNotFound(ex, request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getMessage()).isEqualTo("Staff not found");
        }

        @Test
        @DisplayName("BadRequestException → 400")
        void badRequest_returns400() {
            BadRequestException ex = new BadRequestException("Invalid input");
            ResponseEntity<ApiResponse<?>> resp = handler.handleBadRequest(ex, request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
        }

        @Test
        @DisplayName("ConflictException → 409")
        void conflict_returns409() {
            ConflictException ex = new ConflictException("Duplicate schedule");
            ResponseEntity<ApiResponse<?>> resp = handler.handleConflict(ex, request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
        }

        @Test
        @DisplayName("ForbiddenOperationException → 403")
        void forbiddenOperation_returns403() {
            ForbiddenOperationException ex = new ForbiddenOperationException("Cannot delete last admin");
            ResponseEntity<ApiResponse<?>> resp = handler.handleForbiddenOperation(ex, request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HTTP / Infrastructure exceptions
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("HTTP / Infrastructure exceptions")
    class HttpExceptions {

        @Test
        @DisplayName("BUG-C2: DataIntegrityViolationException → 409, no raw SQL in response")
        void dataIntegrityViolation_returns409_noSqlLeakage() {
            DataIntegrityViolationException ex = new DataIntegrityViolationException(
                    "UPDATE compensation_day SET schedule_id = NULL WHERE schedule_id IN (1,2,3)");

            ResponseEntity<ApiResponse<?>> resp = handler.handleDataIntegrityViolation(ex, request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            String msg = resp.getBody().getMessage();
            // Must NOT echo the raw SQL
            assertThat(msg).isNotEqualTo(ex.getMessage());
            assertThat(msg).doesNotContain("UPDATE", "DELETE", "WHERE", "schedule_id", "IN (");
        }

        @Test
        @DisplayName("BUG-C2: FK constraint → 409 with descriptive message")
        void dataIntegrityViolation_fk_returns409() {
            DataIntegrityViolationException ex = new DataIntegrityViolationException(
                    "could not execute statement",
                    new SQLException("foreign key constraint fails"));

            ResponseEntity<ApiResponse<?>> resp = handler.handleDataIntegrityViolation(ex, request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            String msg = resp.getBody().getMessage();
            // Must be a descriptive Vietnamese message, not raw SQL
            assertThat(msg).isNotEqualTo(ex.getMessage());
            assertThat(msg.length()).isGreaterThan(10);
        }

        @Test
        @DisplayName("BUG-M1: NoHandlerFoundException → 404")
        void noHandlerFound_returns404() {
            NoHandlerFoundException ex = new NoHandlerFoundException("GET", "/unknown", null);
            ResponseEntity<ApiResponse<?>> resp = handler.handleNoHandlerFound(ex, request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            // The handler uses the actual request URI, not the NoHandlerFoundException path
            assertThat(resp.getBody().getMessage()).contains("/api/v1/test");
        }

        @Test
        @DisplayName("BUG-M2: HttpMessageNotReadableException (null body) → 400")
        void httpMessageNotReadable_returns400() {
            HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                    "Required request body is missing", (Throwable) null, null);
            ResponseEntity<ApiResponse<?>> resp = handler.handleHttpMessageNotReadable(ex, request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
        }

        @Test
        @DisplayName("BUG-M4: Malformed JSON date → 400 with date hint")
        void malformedDate_returns400_withHint() {
            HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                    "Cannot deserialize value of type `java.time.LocalDate` from String \"not-a-date\"",
                    (Throwable) null, null);
            ResponseEntity<ApiResponse<?>> resp = handler.handleHttpMessageNotReadable(ex, request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resp.getBody().getMessage()).contains("YYYY-MM-DD");
        }

        @Test
        @DisplayName("BUG-M3: MethodArgumentTypeMismatchException → 400")
        void methodArgumentTypeMismatch_returns400() {
            MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
            when(ex.getName()).thenReturn("periodId");
            when(ex.getRequiredType()).thenReturn((Class) Integer.class);

            ResponseEntity<ApiResponse<?>> resp = handler.handleMethodArgumentTypeMismatch(ex, request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            assertThat(resp.getBody().getMessage()).contains("periodId").contains("Integer");
        }

        @Test
        @DisplayName("MissingServletRequestParameterException → 400")
        void missingParam_returns400() {
            MissingServletRequestParameterException ex =
                    new MissingServletRequestParameterException("page", "int");
            ResponseEntity<ApiResponse<?>> resp = handler.handleMissingServletRequestParameter(ex, request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resp.getBody().getMessage()).contains("page");
        }

        @Test
        @DisplayName("HttpRequestMethodNotSupportedException → 405")
        void wrongMethod_returns405() {
            HttpRequestMethodNotSupportedException ex =
                    new HttpRequestMethodNotSupportedException("DELETE");
            ResponseEntity<ApiResponse<?>> resp = handler.handleHttpRequestMethodNotSupported(ex, request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
            assertThat(resp.getBody().getMessage()).contains("DELETE");
        }

        @Test
        @DisplayName("MaxUploadSizeExceededException → 413")
        void uploadTooLarge_returns413() {
            MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(5 * 1024 * 1024);
            ResponseEntity<ApiResponse<?>> resp = handler.handleMaxUploadSizeExceeded(ex, request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
            assertThat(resp.getBody().getMessage()).contains("5MB");
        }

        @Test
        @DisplayName("OptimisticLockingFailureException → 409")
        void optimisticLock_returns409() {
            OptimisticLockingFailureException ex = new OptimisticLockingFailureException("Stale data");
            ResponseEntity<ApiResponse<?>> resp = handler.handleOptimisticLocking(ex, request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Validation
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Validation errors")
    class Validation {

        @Test
        @DisplayName("MethodArgumentNotValidException → 400 with field errors")
        void validationError_returns400_withFieldMap() {
            FieldError fe = new FieldError("staff", "username", "must not be blank");
            MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
            org.springframework.validation.BindingResult br = mock(org.springframework.validation.BindingResult.class);
            when(ex.getBindingResult()).thenReturn(br);
            when(br.getAllErrors()).thenReturn(List.of(fe));

            ResponseEntity<ApiResponse<?>> resp = handler.handleValidation(ex, request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            @SuppressWarnings("unchecked")
            Map<String, String> errors = (Map<String, String>) resp.getBody().getData();
            assertThat(errors).containsEntry("username", "must not be blank");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Catch-all (security)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Catch-all (security)")
    class CatchAll {

        @Test
        @DisplayName("Generic Exception → 500 with generic message (no SQL leaked)")
        void genericException_returns500_noSqlLeakage() {
            Exception ex = new RuntimeException(
                    "UPDATE compensation_day SET schedule_id = NULL WHERE schedule_id IN (1,2,3)");
            ResponseEntity<ApiResponse<?>> resp = handler.handleGeneral(ex, request);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().isSuccess()).isFalse();
            // Must NOT echo the SQL in the message
            assertThat(resp.getBody().getMessage()).doesNotContain("UPDATE", "compensation_day", "schedule_id");
            assertThat(resp.getBody().getMessage()).doesNotContain(ex.getMessage());
        }
    }
}
