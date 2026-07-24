-- V14: Add rebalance rounds runtime config keys (Commit C)
-- These round counts control how aggressively the rebalancer moves
-- shifts between staff during post-processing.
-- Value 0 = disable rebalance for that key.

INSERT IGNORE INTO algorithm_config (param_key, created_at, description, param_value, updated_at, value_type, updated_by)
VALUES ('rebalance_rounds_total', NOW(),
        'Số vòng lặp rebalance tổng (RRHC totalCountRebalance, SA fairnessRebalance). Đặt 0 để tắt. Mặc định 80.',
        '80', NOW(), 'NUMBER', NULL);

INSERT IGNORE INTO algorithm_config (param_key, created_at, description, param_value, updated_at, value_type, updated_by)
VALUES ('rebalance_rounds_per_type', NOW(),
        'Số vòng lặp rebalance per-type (RRHC perTypeRebalance, Beam perTypeRebalance). Đặt 0 để tắt. Mặc định 30.',
        '30', NOW(), 'NUMBER', NULL);

INSERT IGNORE INTO algorithm_config (param_key, created_at, description, param_value, updated_at, value_type, updated_by)
VALUES ('rebalance_rounds_eg', NOW(),
        'Số vòng lặp rebalance EG perTypeMoveRebalance. Đặt 0 để tắt. Mặc định 40.',
        '40', NOW(), 'NUMBER', NULL);

INSERT IGNORE INTO algorithm_config (param_key, created_at, description, param_value, updated_at, value_type, updated_by)
VALUES ('rebalance_rounds_post_save', NOW(),
        'Số vòng lặp post-process rebalance khi lưu (AutoSchedulingService.optimizeFairnessBySafeReassignment). Đặt 0 để tắt. Mặc định 100.',
        '100', NOW(), 'NUMBER', NULL);
