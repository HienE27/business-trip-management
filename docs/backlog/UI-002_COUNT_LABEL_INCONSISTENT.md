# UI-002 — Algorithm Config count label inconsistent after filtering

**Ticket**: UI-002
**Severity**: P3 (Minor UX)
**Release impact**: NON-BLOCKING, v1.1.0
**Created**: 2026-07-18
**Created by**: Tech Lead review (RC-001 / UI-001 post-fix)

---

## Problem Statement

In `CustomConfigsCard.tsx`, the label shows raw API count while the table body renders filtered count.

```typescript
// Line 77 — shows raw API response count
<span className="...">{configs.length} thông số</span>

// Table body — filtered array (LEGACY_AUTO_GEN_KEYS excluded)
{filtered.map(config => (...))}
```

**Before fix**: Label says `40`, table renders `39` (10 `auto_gen_*` keys filtered)
**After UI-001 fix**: Label says `40`, table renders `39` (10 `auto_gen_*` + `auto_compensation_enabled` filtered)

This has been true since the original `LEGACY_AUTO_GEN_KEYS` filter was introduced. It was never incorrect from a technical standpoint (the count reflects the API response size), but it creates a minor UX inconsistency.

---

## Proposed Fix

Change line 77 in `CustomConfigsCard.tsx`:

```typescript
// FROM
<span className="...">{configs.length} thông số</span>

// TO
<span className="...">{filtered.length} thông số</span>
```

**Scope**: 1 file, 1 line.

---

## Why NOT in UI-001

UI-001 was scoped strictly to "hide the deprecated toggle". The count-label inconsistency is a pre-existing issue that was present long before UI-001 was identified. Mixing it into UI-001 would violate scope discipline and create unnecessary regression risk.

---

## Acceptance Criteria

- [ ] Label shows filtered count (matches visible table rows)
- [ ] Search/filter changes the label count in real-time
- [ ] Pagination preserves correct count
- [ ] Typecheck passes
- [ ] Lint passes

---

## Reference

- `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/CustomConfigsCard.tsx` line 77
- Related: `docs/RC_v1.0.0_FIXLIST.md` (UI-001)
