# Bug #001 — Apply Profile fails with 422: validation mismatch

**Severity**: 🔴 **HIGH** — Blocks the core "apply profile" workflow
**Discovered**: 2026-07-21 03:57 during Gate 2 smoke test
**Status**: 🟡 Under investigation

---

## Reproduction Steps

1. Login as admin → get JWT token
2. `POST /api/v1/config/profiles` with valid `CreateProfileRequest` → returns 201 + profile with snapshot
3. `POST /api/v1/config/profiles/{id}/apply` → returns **422 Unprocessable Entity**

## Expected

Apply should succeed, because:
- The profile's `config` was just **cloned from the current active config** (via `POST /api/v1/config/profiles`)
- If the current active config is valid (which it must be, since it's been running), the cloned config should also be valid

## Actual

422 with 9 validation errors:

| # | fieldPath | message | severity |
|---|---|---|---|
| 1 | `removedShiftTypes` | "Loại trừ ca xếp có giá trị không hợp lệ" | ERROR |
| 2 | `coverage.l01.minPerDay` | "L01 - Tối thiểu/ngày phải ≥ 1" | ERROR |
| 3 | `coverage.l01.maxPerDay` | "L01 - Tối đa/ngày phải ≥ 1" | ERROR |
| 4 | `coverage.l02.minPerDay` | "L02 - Tối thiểu/ngày phải ≥ 1" | ERROR |
| 5 | `coverage.l02.maxPerDay` | "L02 - Tối đa/ngày phải ≥ 1" | ERROR |
| 6 | `coverage.l03.minPerDay` | "L03 - Tối thiểu/ngày phải ≥ 1" | ERROR |
| 7 | `coverage.l03.maxPerDay` | "L03 - Tối đa/ngày phải ≥ 1" | ERROR |
| 8 | `coverage.l04.minPerDay` | "L04 - Tối thiểu/ngày phải ≥ 1" | ERROR |
| 9 | `coverage.l04.maxPerDay` | "L04 - Tối đa/ngày phải ≥ 1" | ERROR |

## Root Cause Hypothesis

The profile's `config` (returned from `POST /api/v1/config/profiles`) contains:

```json
{
  "l01MinPerDay": 0,  // = 0
  "l01MaxPerDay": 0,  // = 0
  "l02MinPerDay": 0,  // = 0
  // ... etc
}
```

But validation requires `minPerDay ≥ 1` and `maxPerDay ≥ 1`.

**Three possible causes**:

1. **Hypothesis A** — Validation is correct, but `ConfigDefaults` is wrong (returns 0 for minPerDay/maxPerDay)
2. **Hypothesis B** — Create profile's snapshot mapping is broken (clones wrong fields)
3. **Hypothesis C** — Intentional: only validate on apply, not on create (loose on creation, strict on apply)

To distinguish, we need to check:

```sql
-- What is the actual active config in DB?
SELECT * FROM algorithm_config ORDER BY updated_at DESC LIMIT 1;
```

If `algorithm_config.l01_min_per_day = 0`, then Hypothesis A is correct.
If `algorithm_config.l01_min_per_day >= 1` but profile's `l01MinPerDay = 0`, then Hypothesis B.

## Evidence Files

- `docs/evidence/gate2/smoke/02-create-profile.json` — profile snapshot (raw)
- `docs/evidence/gate2/smoke/bug-001-apply-422.json` — 422 error body (raw)
- `docs/evidence/gate2/smoke/08-apply-profile.json` — failed response

## Verification Steps for Tech Lead

1. ~~Run SQL query above to determine Hypothesis A vs B~~
2. **VERIFIED via REST**: `GET /api/v1/auto-schedule/config` returns:
   - `auto_gen_l01_min_per_day = 0` ❌ (should be 1)
   - `auto_gen_l01_max_per_day = 0` ❌ (should be 10)
   - `auto_gen_l02_min_per_day = 0` ❌ (should be 1)
   - `auto_gen_l02_max_per_day = 0` ❌ (should be 10)
3. Check seed/migration: `backend/src/main/resources/db/migration/*.sql` for INSERT into `algorithm_config`
4. **Confirmed Hypothesis A**: Data in DB ≠ ConfigDefaults

## Root Cause (CONFIRMED)

**`ConfigDefaults.withDefaults()` returns correct values (minPerDay=1, maxPerDay=10), BUT the `algorithm_config` table contains rows with values `0` from legacy seed/migration.**

Specifically:
- DB row `auto_gen_l01_min_per_day = "0"` (string)
- Code default `L01_MIN_PER_DAY = 1` (int)
- These are NOT in sync

When `ConfigProfileService.create()` does:
```java
ConfigDomain currentConfig = configService.load();  // ← loads from DB → returns 0
.configJson(toJson(currentConfig))                   // ← snapshot has 0
```

The profile's `configJson` contains `l01MinPerDay: 0`, which fails validation when applied.

## Affected Components

- **Database**: `algorithm_config` table — rows with values `0` for L01-L04 coverage bounds
- **Migration scripts**: Likely V5__add_algorithm_config_audit.sql or earlier
- **ConfigMapper**: Reads from DB correctly (no fix needed)
- **ConfigDefaults**: Correct, but never applied because `ConfigService.load()` doesn't merge with defaults

## Recommended Fix

Two options:

**Option A — Data migration (recommended)**: Add migration script to set values from `ConfigDefaults`:

```sql
-- V20__sync_algorithm_config_with_defaults.sql
UPDATE algorithm_config SET param_value = '1'  WHERE param_key = 'auto_gen_l01_min_per_day';
UPDATE algorithm_config SET param_value = '10' WHERE param_key = 'auto_gen_l01_max_per_day';
-- ... etc for L02, L03, L04
```

**Option B — Code fix in `ConfigService.load()`**: Merge with defaults when value is missing or 0:

```java
public ConfigDomain load() {
    Map<String, String> paramMap = crud.loadConfigCache();
    ConfigDomain loaded = ConfigMapper.fromParamMap(paramMap);
    return ConfigDefaults.withDefaults().merge(loaded);  // ← merge, override defaults with DB
}
```

Option A is cleaner because it preserves DB as source of truth.

## Impact

- **Blocks**: Any user trying to create + apply a custom profile
- **Affects**: All existing custom profiles (none yet, since DB was empty)
- **Production risk**: If DB is in production, scheduler may have been running with `minPerDay=0` (unintended)

## Evidence Files

- `docs/evidence/gate2/smoke/02-create-profile.json` — profile snapshot (raw)
- `docs/evidence/gate2/smoke/bug-001-apply-422.json` — 422 error body (raw)
- `docs/evidence/gate2/smoke/08-apply-profile.json` — failed response
- `docs/evidence/gate2/smoke/12-all-active-config.json` — DB state proving Hypothesis A

---

## Status

✅ **RESOLVED** — V20 migration applied, DB values synced with ConfigDefaults

## Evidence

- V20 migration: `backend/src/main/resources/db/migration/V20__sync_algorithm_config_with_defaults.sql`
- DB state post-fix: `smoke/14-config-after-v20.json`
- Apply test: `smoke/smoke-03-apply.json` → 200 OK