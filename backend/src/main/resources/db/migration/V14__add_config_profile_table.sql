-- =============================================================================
-- V14: Add config_profile table for profile management system
-- =============================================================================

CREATE TABLE IF NOT EXISTS config_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_key VARCHAR(64) NOT NULL UNIQUE,
    name_vi VARCHAR(128) NOT NULL,
    name_en VARCHAR(128),
    description VARCHAR(512),
    category VARCHAR(32) DEFAULT 'GENERAL',
    icon VARCHAR(64) DEFAULT 'tune',
    tags JSON,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_favorite BOOLEAN NOT NULL DEFAULT FALSE,
    config_json JSON NOT NULL,
    created_by VARCHAR(128),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_profile_key (profile_key),
    INDEX idx_category (category),
    INDEX idx_is_system (is_system),
    INDEX idx_is_default (is_default),
    INDEX idx_is_favorite (is_favorite)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- Insert system profiles
-- =============================================================================

-- Balanced: Default profile for general use
INSERT INTO config_profile (profile_key, name_vi, name_en, description, category, icon, is_system, is_default, config_json, created_at)
VALUES (
    'balanced',
    'Cân bằng',
    'Balanced',
    'Cấu hình mặc định, cân bằng giữa chất lượng và tốc độ.',
    'GENERAL',
    'balance',
    TRUE,
    TRUE,
    '{"enabled":true,"holidayMode":"SKIP","removedShiftTypes":[],"maxIterations":500,"neighborhoodSize":10,"tabuTenureMin":5,"tabuTenureMax":10,"maxNoImproveIterations":50,"relativeImprovementThreshold":0.001,"diversifyAfterIterations":20,"acceptanceStrategy":"TABU","saInitialTemperature":100.0,"saCoolingRate":0.9995,"saTemperatureMin":0.01,"laMemorySize":10,"gdInitialLevel":1000.0,"gdDecayRate":0.999,"gdMinLevel":0.0,"cvTarget":0.10,"cvWorst":0.50,"weekendWeight":2.5,"l01MinPerDay":1,"l01MaxPerDay":2,"l01MinPerWeek":1,"l01MaxPerWeek":2,"l02MinPerDay":2,"l02MaxPerDay":5,"l02MinPerWeek":2,"l02MaxPerWeek":6,"l03MinPerDay":1,"l03MaxPerDay":3,"l03MinPerWeek":1,"l03MaxPerWeek":4,"l04MinPerDay":1,"l04MaxPerDay":2,"l04MinPerWeek":1,"l04MaxPerWeek":3,"l04CrossSpecialtyEnabled":false,"l04CrossSpecialtyRatio":0.3,"l04AllowedSpecialties":[],"l04BalanceStrategy":"STRICT_MATCH_ONLY","overnightRecoveryHours":24,"autoCompensationEnabled":true,"greedyCoverageThreshold":0.85,"minStaffPerShift":0,"maxStaffPerShift":0,"minShiftsPerStaff":0,"maxShiftsPerStaff":0,"timeLimitSeconds":60,"candidateListSize":50}',
    NOW()
);

-- Emergency: Fast, prioritize speed
INSERT INTO config_profile (profile_key, name_vi, name_en, description, category, icon, is_system, is_default, config_json, created_at)
VALUES (
    'emergency',
    'Khẩn cấp',
    'Emergency',
    'Tối ưu cho tình huống khẩn cấp, chạy nhanh với kết quả chấp nhận được.',
    'EMERGENCY',
    'emergency',
    TRUE,
    FALSE,
    '{"enabled":true,"holidayMode":"PARTIAL","removedShiftTypes":[],"maxIterations":100,"neighborhoodSize":5,"tabuTenureMin":3,"tabuTenureMax":5,"maxNoImproveIterations":20,"relativeImprovementThreshold":0.005,"diversifyAfterIterations":10,"acceptanceStrategy":"TABU","saInitialTemperature":50.0,"saCoolingRate":0.99,"saTemperatureMin":0.1,"laMemorySize":5,"gdInitialLevel":500.0,"gdDecayRate":0.99,"gdMinLevel":0.0,"cvTarget":0.15,"cvWorst":0.60,"weekendWeight":1.5,"l01MinPerDay":1,"l01MaxPerDay":3,"l01MinPerWeek":1,"l01MaxPerWeek":3,"l02MinPerDay":1,"l02MaxPerDay":4,"l02MinPerWeek":1,"l02MaxPerWeek":5,"l03MinPerDay":1,"l03MaxPerDay":2,"l03MinPerWeek":1,"l03MaxPerWeek":3,"l04MinPerDay":1,"l04MaxPerDay":2,"l04MinPerWeek":1,"l04MaxPerWeek":2,"l04CrossSpecialtyEnabled":false,"l04CrossSpecialtyRatio":0.5,"l04AllowedSpecialties":[],"l04BalanceStrategy":"FAIR_DISTRIBUTE","overnightRecoveryHours":12,"autoCompensationEnabled":true,"greedyCoverageThreshold":0.70,"minStaffPerShift":0,"maxStaffPerShift":0,"minShiftsPerStaff":0,"maxShiftsPerStaff":0,"timeLimitSeconds":30,"candidateListSize":30}',
    NOW()
);

-- High Coverage: Prioritize meeting minimum staff requirements
INSERT INTO config_profile (profile_key, name_vi, name_en, description, category, icon, is_system, is_default, config_json, created_at)
VALUES (
    'high-coverage',
    'Phủ sóng cao',
    'High Coverage',
    'Ưu tiên đảm bảo đủ nhân sự cho mọi ca trực.',
    'COVERAGE',
    'verified_user',
    TRUE,
    FALSE,
    '{"enabled":true,"holidayMode":"SKIP","removedShiftTypes":[],"maxIterations":800,"neighborhoodSize":15,"tabuTenureMin":8,"tabuTenureMax":15,"maxNoImproveIterations":80,"relativeImprovementThreshold":0.0005,"diversifyAfterIterations":30,"acceptanceStrategy":"TABU","saInitialTemperature":150.0,"saCoolingRate":0.9999,"saTemperatureMin":0.001,"laMemorySize":15,"gdInitialLevel":1500.0,"gdDecayRate":0.9999,"gdMinLevel":0.0,"cvTarget":0.12,"cvWorst":0.55,"weekendWeight":3.0,"l01MinPerDay":2,"l01MaxPerDay":3,"l01MinPerWeek":2,"l01MaxPerWeek":3,"l02MinPerDay":3,"l02MaxPerDay":6,"l02MinPerWeek":3,"l02MaxPerWeek":7,"l03MinPerDay":2,"l03MaxPerDay":4,"l03MinPerWeek":2,"l03MaxPerWeek":5,"l04MinPerDay":2,"l04MaxPerDay":3,"l04MinPerWeek":2,"l04MaxPerWeek":4,"l04CrossSpecialtyEnabled":true,"l04CrossSpecialtyRatio":0.4,"l04AllowedSpecialties":[],"l04BalanceStrategy":"WEIGHTED_FAIR","overnightRecoveryHours":24,"autoCompensationEnabled":true,"greedyCoverageThreshold":0.95,"minStaffPerShift":2,"maxStaffPerShift":5,"minShiftsPerStaff":4,"maxShiftsPerStaff":12,"timeLimitSeconds":120,"candidateListSize":75}',
    NOW()
);

-- High Fairness: Prioritize even distribution of shifts
INSERT INTO config_profile (profile_key, name_vi, name_en, description, category, icon, is_system, is_default, config_json, created_at)
VALUES (
    'high-fairness',
    'Công bằng cao',
    'High Fairness',
    'Ưu tiên phân bổ ca trực đều nhau giữa các nhân viên.',
    'FAIRNESS',
    'groups',
    TRUE,
    FALSE,
    '{"enabled":true,"holidayMode":"SKIP","removedShiftTypes":[],"maxIterations":600,"neighborhoodSize":12,"tabuTenureMin":6,"tabuTenureMax":12,"maxNoImproveIterations":60,"relativeImprovementThreshold":0.001,"diversifyAfterIterations":25,"acceptanceStrategy":"TABU","saInitialTemperature":100.0,"saCoolingRate":0.999,"saTemperatureMin":0.01,"laMemorySize":12,"gdInitialLevel":1000.0,"gdDecayRate":0.999,"gdMinLevel":0.0,"cvTarget":0.05,"cvWorst":0.30,"weekendWeight":4.0,"l01MinPerDay":1,"l01MaxPerDay":2,"l01MinPerWeek":1,"l01MaxPerWeek":2,"l02MinPerDay":2,"l02MaxPerDay":5,"l02MinPerWeek":2,"l02MaxPerWeek":5,"l03MinPerDay":1,"l03MaxPerDay":3,"l03MinPerWeek":1,"l03MaxPerWeek":3,"l04MinPerDay":1,"l04MaxPerDay":2,"l04MinPerWeek":1,"l04MaxPerWeek":2,"l04CrossSpecialtyEnabled":true,"l04CrossSpecialtyRatio":0.3,"l04AllowedSpecialties":[],"l04BalanceStrategy":"FAIR_DISTRIBUTE","overnightRecoveryHours":24,"autoCompensationEnabled":true,"greedyCoverageThreshold":0.90,"minStaffPerShift":0,"maxStaffPerShift":0,"minShiftsPerStaff":0,"maxShiftsPerStaff":10,"timeLimitSeconds":90,"candidateListSize":60}',
    NOW()
);

-- Holiday: Optimized for holiday periods
INSERT INTO config_profile (profile_key, name_vi, name_en, description, category, icon, is_system, is_default, config_json, created_at)
VALUES (
    'holiday',
    'Ngày nghỉ',
    'Holiday',
    'Cấu hình cho ngày nghỉ lễ, Tết với staffing constraints đặc biệt.',
    'HOLIDAY',
    'celebration',
    TRUE,
    FALSE,
    '{"enabled":true,"holidayMode":"PARTIAL","removedShiftTypes":["L03","L04"],"maxIterations":400,"neighborhoodSize":8,"tabuTenureMin":4,"tabuTenureMax":8,"maxNoImproveIterations":40,"relativeImprovementThreshold":0.002,"diversifyAfterIterations":15,"acceptanceStrategy":"TABU","saInitialTemperature":80.0,"saCoolingRate":0.995,"saTemperatureMin":0.05,"laMemorySize":8,"gdInitialLevel":800.0,"gdDecayRate":0.995,"gdMinLevel":0.0,"cvTarget":0.18,"cvWorst":0.65,"weekendWeight":1.0,"l01MinPerDay":1,"l01MaxPerDay":2,"l01MinPerWeek":1,"l01MaxPerWeek":2,"l02MinPerDay":2,"l02MaxPerDay":4,"l02MinPerWeek":2,"l02MaxPerWeek":4,"l03MinPerDay":0,"l03MaxPerDay":0,"l03MinPerWeek":0,"l03MaxPerWeek":0,"l04MinPerDay":0,"l04MaxPerDay":0,"l04MinPerWeek":0,"l04MaxPerWeek":0,"l04CrossSpecialtyEnabled":false,"l04CrossSpecialtyRatio":0.0,"l04AllowedSpecialties":[],"l04BalanceStrategy":"STRICT_MATCH_ONLY","overnightRecoveryHours":24,"autoCompensationEnabled":false,"greedyCoverageThreshold":0.80,"minStaffPerShift":1,"maxStaffPerShift":3,"minShiftsPerStaff":0,"maxShiftsPerStaff":6,"timeLimitSeconds":45,"candidateListSize":40}',
    NOW()
);

-- Fast: Quick execution with acceptable quality
INSERT INTO config_profile (profile_key, name_vi, name_en, description, category, icon, is_system, is_default, config_json, created_at)
VALUES (
    'fast',
    'Nhanh',
    'Fast',
    'Chạy nhanh trong thời gian ngắn, phù hợp cho preview.',
    'GENERAL',
    'speed',
    TRUE,
    FALSE,
    '{"enabled":true,"holidayMode":"SKIP","removedShiftTypes":[],"maxIterations":200,"neighborhoodSize":6,"tabuTenureMin":3,"tabuTenureMax":6,"maxNoImproveIterations":25,"relativeImprovementThreshold":0.01,"diversifyAfterIterations":8,"acceptanceStrategy":"TABU","saInitialTemperature":30.0,"saCoolingRate":0.98,"saTemperatureMin":0.5,"laMemorySize":4,"gdInitialLevel":300.0,"gdDecayRate":0.98,"gdMinLevel":0.0,"cvTarget":0.20,"cvWorst":0.70,"weekendWeight":2.0,"l01MinPerDay":1,"l01MaxPerDay":2,"l01MinPerWeek":1,"l01MaxPerWeek":2,"l02MinPerDay":2,"l02MaxPerDay":4,"l02MinPerWeek":2,"l02MaxPerWeek":5,"l03MinPerDay":1,"l03MaxPerDay":2,"l03MinPerWeek":1,"l03MaxPerWeek":3,"l04MinPerDay":1,"l04MaxPerDay":2,"l04MinPerWeek":1,"l04MaxPerWeek":2,"l04CrossSpecialtyEnabled":false,"l04CrossSpecialtyRatio":0.2,"l04AllowedSpecialties":[],"l04BalanceStrategy":"STRICT_MATCH_ONLY","overnightRecoveryHours":24,"autoCompensationEnabled":true,"greedyCoverageThreshold":0.75,"minStaffPerShift":0,"maxStaffPerShift":0,"minShiftsPerStaff":0,"maxShiftsPerStaff":0,"timeLimitSeconds":20,"candidateListSize":25}',
    NOW()
);
