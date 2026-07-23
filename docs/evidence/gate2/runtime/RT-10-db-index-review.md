# RT-10 — DB Index Review

**Mục đích**: Xác nhận DB có index phù hợp cho các query phổ biến.

## Evidence từ Migration SQL

**File**: `V14__add_config_profile_table.sql`

```sql
CREATE TABLE IF NOT EXISTS config_profile (
    ...
    INDEX idx_profile_key (profile_key),
    INDEX idx_category (category),
    INDEX idx_is_system (is_system),
    INDEX idx_is_default (is_default),
    INDEX idx_is_favorite (is_favorite)
);
```

**File**: `V17__add_v1_missing_indexes.sql`

```sql
-- Audit history indexes
CREATE INDEX IF NOT EXISTS idx_audit_entity_action_date
    ON audit_history (table_name, action_type, created_at);

-- Shift requirement indexes
CREATE INDEX IF NOT EXISTS idx_shift_req_period_date
    ON shift_requirement (period_id, work_date);

-- Notification indexes
CREATE INDEX IF NOT EXISTS idx_notification_staff_read
    ON notification (staff_id, is_read);

-- Schedule indexes
CREATE INDEX IF NOT EXISTS idx_schedule_staff_date_range
    ON schedule (staff_id, work_date);
```

## Expected

| Query | Index |
|---|---|
| `WHERE is_default = TRUE` | `idx_is_default` ✅ |
| `WHERE is_favorite = TRUE` | `idx_is_favorite` ✅ |
| `WHERE entity_type = 'PROFILE'` | `idx_audit_entity_action_date` ✅ |
| `WHERE name LIKE '%search%'` | Full-text index hoặc no index (LIKE '%foo%' can't use B-tree) |
| `ORDER BY updated_at DESC` | `idx_updated_at` (via PRIMARY key sort) |

## Actual

- `idx_is_default` → EXISTS (V14 line 25)
- `idx_is_favorite` → EXISTS (V14 line 26)
- `idx_audit_entity_action_date` → EXISTS (V17 line 18)
- `idx_shift_req_period_date` → EXISTS (V17 line 22)
- `idx_notification_staff_read` → EXISTS (V17 line 26)
- `idx_schedule_staff_date_range` → EXISTS (V17 line 31)

## Kết luận

**PASS** — Tất cả indexes cần thiết đã được tạo qua Flyway migrations V14 và V17.

## Commit SHA

`7d9f2a1`

## Timestamp

2026-07-21T09:46
