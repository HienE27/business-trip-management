-- V15: Digital Twin Sandbox Tables
-- v11.1.1 Sandbox Engine

-- ─── Sandbox Session ───────────────────────────────────────────────────────────

CREATE TABLE sandbox_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_key VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    simulation_mode VARCHAR(32),
    created_by VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME,
    expires_at DATETIME,
    source_period_id INT,
    profile_id BIGINT,
    current_snapshot_id BIGINT,
    iterations INT DEFAULT 0,
    best_score DOUBLE,
    initial_score DOUBLE,
    coverage_rate DOUBLE,
    fairness_cv DOUBLE,
    violations INT DEFAULT 0,
    runtime_seconds INT DEFAULT 0,
    error_message VARCHAR(1024),
    ttl_hours INT DEFAULT 24,
    is_pinned BOOLEAN DEFAULT FALSE,
    description VARCHAR(512),

    INDEX idx_sandbox_status (status),
    INDEX idx_sandbox_user (created_by),
    INDEX idx_sandbox_expires (expires_at),
    INDEX idx_sandbox_period (source_period_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── Sandbox Snapshot ─────────────────────────────────────────────────────────

CREATE TABLE sandbox_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    iteration INT NOT NULL,
    score DOUBLE NOT NULL,
    coverage_rate DOUBLE,
    fairness_cv DOUBLE,
    violations INT,
    move_type VARCHAR(32),
    staff_id INT,
    slot_id INT,
    target_staff_id INT,
    score_delta DOUBLE,
    accepted BOOLEAN,
    acceptance_probability DOUBLE,
    temperature DOUBLE,
    tabu_remaining INT,
    constraint_deltas TEXT,
    state_json LONGTEXT,
    delta_json LONGTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    memory_bytes BIGINT,
    is_checkpoint BOOLEAN DEFAULT FALSE,
    metadata TEXT,

    INDEX idx_snapshot_session (session_id),
    INDEX idx_snapshot_iteration (session_id, iteration),
    FOREIGN KEY (session_id) REFERENCES sandbox_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── Sandbox Assignment ────────────────────────────────────────────────────────

CREATE TABLE sandbox_assignment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    slot_id INT NOT NULL,
    work_date DATETIME NOT NULL,
    shift_type_id VARCHAR(8) NOT NULL,
    staff_id INT,
    staff_name VARCHAR(128),
    is_simulated BOOLEAN DEFAULT FALSE,
    has_conflict BOOLEAN DEFAULT FALSE,
    score_contribution DOUBLE,
    iteration_changed INT,
    move_type VARCHAR(32),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME,

    INDEX idx_sandbox_assign_session (session_id),
    INDEX idx_sandbox_assign_slot (session_id, slot_id),
    FOREIGN KEY (session_id) REFERENCES sandbox_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
