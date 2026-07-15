package com.hospital.scheduler.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when an action would violate a business rule that the caller can't
 * (or shouldn't be allowed to) bypass — for example, revoking a permission
 * that would lock the system out of basic administration.
 *
 * <p>Maps to HTTP 422 (Unprocessable Entity) — the request was well-formed,
 * but the server refuses to act because doing so would violate a domain rule.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}