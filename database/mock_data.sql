USE hospital_scheduler;

-- =====================================================
-- SHIFT TYPES
-- =====================================================

INSERT INTO shift_type (
    id,
    name,
    fatigue_score
)
VALUES
(
    'L01',
    'Truc 24/24',
    5
),
(
    'L02',
    'Thong Tam',
    3
),
(
    'L03',
    'Dich Vu',
    2
),
(
    'L04',
    'Chuyen Gia',
    4
);

-- =====================================================
-- STAFF
-- =====================================================

INSERT INTO staff (

    username,
    password_hash,
    full_name,
    role,
    specialty,
    max_shifts_per_month,
    phone,
    email

)
VALUES
(
    'doctor01',
    '123',
    'Nguyen Van A',
    'STAFF',
    'SAN_NHI',
    5,
    '0900000001',
    'doctor01@hospital.com'
),
(
    'doctor02',
    '123',
    'Tran Thi B',
    'STAFF',
    'NOI_TIET',
    5,
    '0900000002',
    'doctor02@hospital.com'
),
(
    'doctor03',
    '123',
    'Le Van C',
    'STAFF',
    'CHUYEN_GIA',
    5,
    '0900000003',
    'doctor03@hospital.com'
);

-- =====================================================
-- LEAVE REQUEST
-- =====================================================

INSERT INTO leave_request (

    staff_id,
    request_date,
    reason,
    status

)
VALUES
(
    1,
    '2026-06-05',
    'Family work',
    'APPROVED'
),
(
    2,
    '2026-06-10',
    'Personal leave',
    'APPROVED'
);

-- =====================================================
-- SCHEDULE PERIOD
-- =====================================================

INSERT INTO schedule_period (

    period_name,
    status,
    generated_by

)
VALUES
(
    '2026-06',
    'DRAFT',
    'admin'
);

-- =====================================================
-- ALGORITHM CONFIG
-- =====================================================

INSERT INTO algorithm_config (

    param_key,
    param_value,
    description

)
VALUES
(
    'weight_fairness',
    0.7,
    'Weight for fairness score'
),
(
    'weight_fatigue',
    0.3,
    'Weight for fatigue score'
);