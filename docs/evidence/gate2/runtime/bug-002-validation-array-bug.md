# Bug #002 — Validation fails for chipGroup fields (String[] arrays)

**Severity**: 🔴 **HIGH** — Blocks PUT/apply for any profile with chipGroup fields

**Discovered**: 2026-07-21 04:18 during Gate 2 smoke test (post-V20)

---

## Reproduction

1. Create custom profile (creates profile with `removedShiftTypes: []`)
2. PUT update with `removedShiftTypes: ["L01"]` (valid value)
3. **Expected**: 200 OK
4. **Actual**: 422 with validation error

```
{
  "fieldPath": "removedShiftTypes",
  "message": "Loại trừ ca xếp có giá trị không hợp lệ",
  "severity": "ERROR"
}
```

5. Same error happens on POST `/apply` regardless of `removedShiftTypes` value.

## Root Cause

In `ConfigValidator.validateSingleField()`:

```java
String strVal = value.toString();  // ← BUG

// Enum validation
if (meta.hasAllowedValues() && !strVal.isBlank()) {
    boolean valid = false;
    for (ConfigMetadata.Option opt : meta.allowedValues()) {
        if (opt.value().equals(strVal)) {  // ← comparing array.toString() with enum key
            valid = true;
            break;
        }
    }
    if (!valid) {
        return new Violation(...);
    }
}
```

When `value` is a `String[]`:
- Empty array: `value.toString()` returns `"[]"` → never matches `"L01"`, `"L02"`, etc.
- Non-empty: `value.toString()` returns `"[Ljava.lang.String;@1a2b3c4d"` → never matches.

The validator doesn't handle `String[]` specially. It assumes `toString()` produces a comparable value, which only works for primitives, not arrays.

## Affected Fields

Any field with renderType `chipGroup` and value type `String[]`:
- `removedShiftTypes` (currently 4 options: L01, L02, L03, L04)
- `l04.allowedSpecialties` (specialty codes)

## Recommended Fix

```java
// Special handling for String[] (chipGroup)
if (value instanceof String[] arr) {
    // Empty array is always valid
    if (arr.length == 0) return null;
    // Each element must match an allowed option
    for (String s : arr) {
        boolean found = false;
        for (ConfigMetadata.Option opt : meta.allowedValues()) {
            if (opt.value().equals(s)) { found = true; break; }
        }
        if (!found) {
            return new Violation(meta.fieldPath(),
                    meta.labelVi() + " chứa giá trị không hợp lệ: " + s,
                    ConfigMetadata.ValidationSeverity.ERROR);
        }
    }
    return null;
}
```

Place this **before** the existing `strVal = value.toString()` line.

## Evidence Files

- `docs/evidence/gate2/smoke/16-create-after-v20.json` — profile with empty array
- `docs/evidence/gate2/smoke/17-apply-failed-new-profile.json` — empty array 422
- `docs/evidence/gate2/smoke/18-apply-failed-removed-shifts.json` — non-empty array 422

## Affected Operations

- `PUT /api/v1/config/profiles/{id}` (any update with config)
- `POST /api/v1/config/profiles/{id}/apply` (all applies)
- `POST /api/v1/config/profiles/key/{key}/apply` (all applies)

## Impact

- **Blocks**: All profile apply operations
- **Workaround**: None from API; only direct DB manipulation
- **Severity escalation**: Even after fixing V20 migration, this bug prevents any apply

---

# Bug #002 — Validation fails for chipGroup fields (String[] arrays)

**Severity**: 🔴 HIGH → ✅ **RESOLVED**
**Status**: ✅ FIXED

---

## Reproduction

1. Create custom profile (creates profile with `removedShiftTypes: []`)
2. PUT update with `removedShiftTypes: ["L01"]` (valid value)
3. **Expected**: 200 OK ✅
4. **Actual after fix**: 200 OK ✅

## Root Cause

In `ConfigValidator.validateSingleField()`:

```java
String strVal = value.toString();  // ← BUG

// Enum validation
if (meta.hasAllowedValues() && !strVal.isBlank()) {
    boolean valid = false;
    for (ConfigMetadata.Option opt : meta.allowedValues()) {
        if (opt.value().equals(strVal)) {  // ← comparing array.toString() with enum key
            valid = true; break;
        }
    }
    if (!valid) { return new Violation(...); }
}
```

When `value` is `String[]`:
- Empty: `value.toString()` returns `"[]"` → never matches enum values
- Non-empty: `value.toString()` returns `"[Ljava.lang.String;@hashcode"` → never matches

## Fix Applied

**File**: `ConfigValidator.java`

```java
// Special handling for String[] (chipGroup)
if (value instanceof String[] arr) {
    // Empty array is always valid
    if (arr.length == 0) return null;
    // Each element must match an allowed option
    for (String s : arr) {
        boolean found = false;
        for (ConfigMetadata.Option opt : meta.allowedValues()) {
            if (opt.value().equals(s)) { found = true; break; }
        }
        if (!found) {
            return new Violation(meta.fieldPath(),
                    meta.labelVi() + " chứa giá trị không hợp lệ: " + s,
                    ConfigMetadata.ValidationSeverity.ERROR);
        }
    }
    return null;
}
```

## Evidence

- `ConfigValidator.java` — fix applied
- All 29 unit tests pass (total 631 tests)
- Backend rebuild + restart successful
- Apply endpoint returns 200 OK ✅

## Status

✅ **RESOLVED** — Fixed and verified