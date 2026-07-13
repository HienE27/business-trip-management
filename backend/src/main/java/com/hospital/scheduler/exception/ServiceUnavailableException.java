package com.hospital.scheduler.exception;

/**
 * Signal that an upstream dependency (detector, external service) is unavailable
 * and the operation cannot complete safely. Maps to HTTP 503 so the client can
 * distinguish transient infra failure from a real domain error (4xx) or bug (500).
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}