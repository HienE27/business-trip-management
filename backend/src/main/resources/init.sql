-- Hospital Scheduler Database Initialization Script
-- This script runs when MySQL container starts for the first time

-- Create database with UTF8MB4 charset for Vietnamese support
CREATE DATABASE IF NOT EXISTS hospital_scheduler
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE hospital_scheduler;

-- Note: Tables will be created by Hibernate JPA (ddl-auto=update)
-- This init script is for additional setup if needed

-- Example: Create indexes for performance
-- CREATE INDEX IF NOT EXISTS idx_schedule_date ON schedule(work_date);
-- CREATE INDEX IF NOT EXISTS idx_schedule_staff ON schedule(staff_id);

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
