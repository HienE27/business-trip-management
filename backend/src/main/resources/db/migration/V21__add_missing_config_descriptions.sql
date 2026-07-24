-- =====================================================
-- V21: Add Vietnamese descriptions to scheduling_* and balance_score_worst
-- =====================================================
-- Background:
--   The algorithm-config UI (http://localhost:3000/auto-scheduling/algorithm-config)
--   displays the description field for every config row. After initial seeding
--   the following 10 keys were either empty or in English, hurting admin UX:
--
--     * permissions.version
--     * scheduling_max_iterations
--     * scheduling_max_no_improve
--     * scheduling_diversify_after
--     * scheduling_acceptance_strategy
--     * scheduling_candidate_list_size
--     * scheduling_la_memory_size
--     * scheduling_gd_initial_level
--     * scheduling_gd_decay_rate
--     * scheduling_gd_min_level
--     * balance_score_worst
--
-- This migration backfills the description column with the canonical Vietnamese
-- strings defined in code (AlgorithmConfigService#syncDescriptions and
-- PermissionVersionService#bump). The descriptions are aligned with the same
-- source-of-truth so the next code refactor will keep UI and DB in sync.
--
-- Idempotent: only writes the description when the row exists; safe to re-run.
-- =====================================================

UPDATE algorithm_config
   SET description = 'Phiên bản cấu hình phân quyền (epoch ms). Tự động tăng khi bảng role_permission thay đổi; dùng để phát hiện và vô hiệu hóa JWT đã cũ.'
 WHERE param_key = 'permissions.version';

UPDATE algorithm_config
   SET description = 'Số vòng lặp tối đa của thuật toán tối ưu (Tabu Search / Late Acceptance / Great Deluge). Tăng lên nếu thuật toán chưa hết thời gian mà vẫn chưa tìm được lời giải tốt; giảm xuống nếu chạy quá lâu.'
 WHERE param_key = 'scheduling_max_iterations';

UPDATE algorithm_config
   SET description = 'Số vòng lặp liên tiếp không cải thiện trước khi dừng sớm (diversification). Tăng lên → thuật toán kiên trì tìm lời giải tốt hơn; giảm xuống → dừng sớm khi bão hòa.'
 WHERE param_key = 'scheduling_max_no_improve';

UPDATE algorithm_config
   SET description = 'Số vòng lặp không cải thiện liên tiếp kích hoạt diversification (reset/perturb lời giải hiện tại). Giúp tránh local optimum.'
 WHERE param_key = 'scheduling_diversify_after';

UPDATE algorithm_config
   SET description = 'Chiến lược chấp nhận nghiệm xấu hơn: TABU (Tabu Search), SA (Simulated Annealing), LA (Late Acceptance), GD (Great Deluge).'
 WHERE param_key = 'scheduling_acceptance_strategy';

UPDATE algorithm_config
   SET description = 'Số ứng viên lân cận được sinh ra mỗi vòng lặp. Tăng → khám phá rộng hơn, chậm hơn; giảm → hẹp hơn, nhanh hơn.'
 WHERE param_key = 'scheduling_candidate_list_size';

UPDATE algorithm_config
   SET description = 'Độ dài bộ nhớ của Late Acceptance (số vòng lặp trước được so sánh). Lớn hơn → ổn định hơn; nhỏ hơn → phản ứng nhanh hơn.'
 WHERE param_key = 'scheduling_la_memory_size';

UPDATE algorithm_config
   SET description = 'Mực nước ban đầu của Great Deluge (ngưỡng chấp nhận lời giải xấu). Cao → chấp nhận thoáng hơn; thấp → khắt khe hơn.'
 WHERE param_key = 'scheduling_gd_initial_level';

UPDATE algorithm_config
   SET description = 'Tốc độ giảm mực nước Great Deluge mỗi vòng lặp. Gần 1.0 → giảm chậm, tìm kiếm kỹ; nhỏ hơn → giảm nhanh, hội tụ sớm.'
 WHERE param_key = 'scheduling_gd_decay_rate';

UPDATE algorithm_config
   SET description = 'Mực nước tối thiểu của Great Deluge — khi đạt ngưỡng, dừng thuật toán.'
 WHERE param_key = 'scheduling_gd_min_level';

UPDATE algorithm_config
   SET description = 'Hệ số CV (Coefficient of Variation) tải ca trực cho phép ở mức tệ nhất. Thấp → yêu cầu cân bằng chặt; cao → chấp nhận chênh lệch nhiều hơn.'
 WHERE param_key = 'balance_score_worst';
