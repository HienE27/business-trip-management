-- V4: Add missing columns to algorithm_metrics
-- Fix: Unknown column 'total_schedules_created' error

-- Add total_schedules_created column if not exists
ALTER TABLE algorithm_metrics 
ADD COLUMN IF NOT EXISTS total_schedules_created INT DEFAULT 0 AFTER conflict_count;
