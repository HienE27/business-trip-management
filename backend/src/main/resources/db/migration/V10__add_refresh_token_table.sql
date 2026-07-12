-- =====================================================
-- V10: Add refresh_token table
-- =====================================================
-- Persists refresh tokens so we can revoke them (logout,
-- staff deactivation, password change) and detect theft via
-- IP-mismatch alerting.
--
-- Without this table we can only depend on JWT expiry to
-- invalidate tokens, which means a leaked token stays valid
-- for the entire expiry window (24h by default).

CREATE TABLE refresh_token (
    id              INT             NOT NULL AUTO_INCREMENT,
    token_hash      VARCHAR(64)     NOT NULL,
    staff_id        INT             NOT NULL,
    expires_at      DATETIME        NOT NULL,
    revoked_at      DATETIME        NULL,
    issued_ip       VARCHAR(64)     NULL,
    replaced_by_id  INT             NULL,
    created_at      DATETIME        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_token_staff (staff_id),
    KEY idx_refresh_token_replaced_by (replaced_by_id),
    CONSTRAINT fk_refresh_token_staff
        FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_token_replaced_by
        FOREIGN KEY (replaced_by_id) REFERENCES refresh_token(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;