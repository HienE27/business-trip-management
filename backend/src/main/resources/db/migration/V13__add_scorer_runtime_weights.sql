--
-- V13: Add ScheduleQualityScorer runtime weights (Commit I)
--
-- 8 DB keys previously hard-coded as private static final in ScheduleQualityScorer:
--   scorer_coverage_weight              default 0.40
--   scorer_fairness_weight              default 0.35
--   scorer_constraint_weight            default 0.25
--   scorer_pass_threshold               default 80.0
--   scorer_hard_violation_penalty       default 25.0
--   scorer_soft_violation_penalty       default 5.0
--   scorer_target_cv                    default 0.10
--   scorer_worst_cv                     default 0.50
--
-- These mirror @Builder.Default values in AlgorithmRuntimeConfig
-- (single source of truth remains ScheduleQualityScorer.java).
-- Wired into AutoSchedulingService.runScheduling:941-947 and
-- applyPreviewScheduleInternal:481-487 via withWeights/withPassThreshold/
-- withViolationPenalties/withCvTargets.
--
-- Idempotent via INSERT IGNORE so existing DBs that were already seeded
-- (via init.sql or syncDescriptions) don't fail.
--

INSERT IGNORE INTO algorithm_config
    (param_key, created_at, description, param_value, updated_at, value_type, updated_by)
VALUES
    ('scorer_coverage_weight',
     '2026-07-24 00:00:00.000000',
     'Trọng số coverage cho ScheduleQualityScorer (0.0–1.0). Mặc định 0.40. Càng cao càng ưu tiên lấp đầy ca trực.',
     '0.40',
     '2026-07-24 00:00:00.000000',
     'NUMBER',
     NULL),
    ('scorer_fairness_weight',
     '2026-07-24 00:00:00.000000',
     'Trọng số fairness cho ScheduleQualityScorer (0.0–1.0). Mặc định 0.35. Càng cao càng ưu tiên phân bổ công bằng.',
     '0.35',
     '2026-07-24 00:00:00.000000',
     'NUMBER',
     NULL),
    ('scorer_constraint_weight',
     '2026-07-24 00:00:00.000000',
     'Trọng số constraint cho ScheduleQualityScorer (0.0–1.0). Mặc định 0.25. Càng cao càng ưu tiên kỷ luật ràng buộc.',
     '0.25',
     '2026-07-24 00:00:00.000000',
     'NUMBER',
     NULL),
    ('scorer_pass_threshold',
     '2026-07-24 00:00:00.000000',
     'Ngưỡng điểm đạt yêu cầu (0-100). Mặc định 80.0. Lịch có tổng điểm ≥ ngưỡng này được coi là passed.',
     '80.0',
     '2026-07-24 00:00:00.000000',
     'NUMBER',
     NULL),
    ('scorer_hard_violation_penalty',
     '2026-07-24 00:00:00.000000',
     'Phạt điểm cho mỗi vi phạm HARD (BR-01 đến BR-05). Mặc định 25.0.',
     '25.0',
     '2026-07-24 00:00:00.000000',
     'NUMBER',
     NULL),
    ('scorer_soft_violation_penalty',
     '2026-07-24 00:00:00.000000',
     'Phạt điểm cho mỗi vi phạm SOFT (BR-06, BR-07). Mặc định 5.0.',
     '5.0',
     '2026-07-24 00:00:00.000000',
     'NUMBER',
     NULL),
    ('scorer_target_cv',
     '2026-07-24 00:00:00.000000',
     'CV mục tiêu cho fairness. CV ≤ targetCv → 100 điểm fairness. Mặc định 0.10.',
     '0.10',
     '2026-07-24 00:00:00.000000',
     'NUMBER',
     NULL),
    ('scorer_worst_cv',
     '2026-07-24 00:00:00.000000',
     'CV vượt ngưỡng này → 0 điểm fairness. Mặc định 0.50.',
     '0.50',
     '2026-07-24 00:00:00.000000',
     'NUMBER',
     NULL);