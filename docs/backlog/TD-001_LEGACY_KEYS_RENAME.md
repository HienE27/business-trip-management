# TD-001 — Rename LEGACY_AUTO_GEN_KEYS to LEGACY_CONFIG_KEYS

**Ticket**: TD-001
**Severity**: P4 (Naming / Maintainability)
**Release impact**: NON-BLOCKING, v1.1.0
**Created**: 2026-07-18
**Created by**: Tech Lead review (RC-001 / UI-001 post-fix)

---

## Problem Statement

The set `LEGACY_AUTO_GEN_KEYS` in `types.ts` no longer exclusively contains `auto_gen_*` prefixed keys after UI-001 added `auto_compensation_enabled`.

```typescript
// Current — name is misleading
export const LEGACY_AUTO_GEN_KEYS = new Set<string>([
  "auto_compensation_enabled",  // ← NOT auto_gen_* prefix
  "auto_generate_requirements",
  "auto_gen_holiday_mode",
  // ...
]);
```

The current name implies "keys related to auto-generation" but `auto_compensation_enabled` is a standalone scheduling configuration, not part of the auto-gen system. As the set grows to contain more deprecated configuration keys from different subsystems, the name becomes increasingly inaccurate.

---

## Proposed Fix

Rename the constant and its usages from `LEGACY_AUTO_GEN_KEYS` to a more general name:

**Option A** (recommended):
```typescript
export const LEGACY_CONFIG_KEYS = new Set<string>([...]);
```

**Option B** (alternative):
```typescript
export const DEPRECATED_CONFIG_KEYS = new Set<string>([...]);
```

Then update all imports in `CustomConfigsCard.tsx`:
```typescript
import { LEGACY_CONFIG_KEYS } from "./types";
// Usage:
if (LEGACY_CONFIG_KEYS.has(c.paramKey)) return false;
```

**Scope**: 1 constant rename + 2 import references.

---

## Why NOT in UI-001

This is a pure rename/refactor with no behavioral change. It was not identified as part of UI-001's root cause and should not have been included in that fix. Keeping it separate makes the history cleaner (`git log` shows distinct, purposeful commits).

---

## Acceptance Criteria

- [ ] Constant renamed consistently across `types.ts` and `CustomConfigsCard.tsx`
- [ ] Typecheck passes
- [ ] Lint passes
- [ ] Build succeeds
- [ ] Runtime behavior unchanged (filter still works)
- [ ] Could be combined with UI-002 in a single v1.1 "Algorithm Config cleanup" PR if desired

---

## Reference

- `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/types.ts` (constant definition)
- `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/CustomConfigsCard.tsx` (import + usage)
- `docs/RC_v1.0.0_FIXLIST.md` (UI-001 background)
