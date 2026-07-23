package com.hospital.scheduler.security;

import java.lang.annotation.*;

/**
 * Annotation to inject the current authenticated user.
 */
@Documented
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthUser {
}
