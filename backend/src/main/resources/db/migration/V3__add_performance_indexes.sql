-- Database Optimization V3: Additional indexes for auto-scheduling performance
-- Run this script manually if Flyway/Liquibase is not configured
-- These indexes complement the @Index annotations in JPA entities

-- ============================================================
-- Leave Request Indexes (for approved leaves in date range)
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_leave_req_dates ON leave_request(start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_leave_req_status ON leave_request(status);

-- ============================================================
-- Compensation Day Indexes (for date-based queries)
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_comp_day_date ON compensation_day(compensation_date);
CREATE INDEX IF NOT EXISTS idx_comp_day_staff ON compensation_day(staff_id);

-- ============================================================
-- Staff Indexes (for active staff filtering)
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_staff_active ON staff(is_active);

-- ============================================================
-- Algorithm Metrics Indexes (for metrics queries)
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_algo_metrics_period ON algorithm_metrics(period_id);
CREATE INDEX IF NOT EXISTS idx_algo_metrics_type ON algorithm_metrics(algorithm_type);

-- ============================================================
-- Schedule Exchange Indexes (for swap request queries)
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_exchange_requester ON schedule_exchange(requester_id);
CREATE INDEX IF NOT EXISTS idx_exchange_target ON schedule_exchange(target_staff_id);
CREATE INDEX IF NOT EXISTS idx_exchange_status ON schedule_exchange(status);
