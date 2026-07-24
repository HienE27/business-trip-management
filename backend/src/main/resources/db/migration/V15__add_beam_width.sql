--
-- V15: Add beam_width runtime config key (Commit A)
--
-- Previously a dead config: accepted by PUT /runtime-config but never persisted.
-- Now a real DB-backed knob used by BeamSearchScheduler (beam width) and
-- SimulatedAnnealingScheduler (maxIter = beamWidth × 100).
-- Default 5 matches the historical hard-coded DEFAULT_BEAM_WIDTH.
--
-- Idempotent via INSERT IGNORE.
--

INSERT IGNORE INTO algorithm_config
    (param_key, created_at, description, param_value, updated_at, value_type, updated_by)
VALUES
    ('beam_width', NOW(),
     'Độ rộng Beam Search (mặc định 5). Giá trị càng cao → tìm kiếm rộng hơn, quality tốt hơn nhưng chậm hơn. Với SA scheduler, dùng để tính số vòng lặp (beamWidth × 100).',
     '5', NOW(), 'NUMBER', NULL);
