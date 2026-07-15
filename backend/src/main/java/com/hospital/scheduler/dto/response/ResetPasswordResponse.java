package com.hospital.scheduler.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Returned ONLY from the admin password-reset endpoint. The {@code tempPassword}
 * is the plaintext temporary password that the admin must hand to the staff
 * member — the same value is what the backend hashes and persists, so this is
 * the only chance to surface it. Subsequent calls regenerate a new value.
 *
 * <p>The {@code staffId} and {@code username} are echoed so the admin's UI can
 * unambiguously identify which account got reset (the same admin can reset
 * multiple staff in a row from a modal).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResetPasswordResponse {

    private Integer staffId;
    private String username;
    private String tempPassword;
    private String message;
}
