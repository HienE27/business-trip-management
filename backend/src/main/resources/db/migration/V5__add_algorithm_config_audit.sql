-- V5: Add algorithm_config_audit table for change tracking

CREATE TABLE IF NOT EXISTS algorithm_config_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    param_key VARCHAR(50) NOT NULL,
    old_value VARCHAR(2000) NULL,
    new_value VARCHAR(2000) NOT NULL,
    action VARCHAR(20) NOT NULL,
    changed_by BIGINT NULL,
    changed_by_username VARCHAR(100) NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_audit_param_key (param_key),
    INDEX idx_audit_created_at (created_at),
    INDEX idx_audit_user (changed_by),
    CONSTRAINT fk_audit_changed_by FOREIGN KEY (changed_by) REFERENCES staff(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;