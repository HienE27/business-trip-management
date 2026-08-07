# PR3 — Regression Manifest (Modal Migration)

## Status

After review feedback, all flagged regressions are resolved. This document
records every intentional change from PR3 in a reviewer-friendly format.

## Format

| Column          | Meaning                                                |
| --------------- | ------------------------------------------------------ |
| Regression      | What changed                                            |
| Intentional     | Yes = planned, part of migration. No = bug, must fix.  |
| User Visible    | Yes = end-user sees difference. No = internal cleanup.  |
| Follow-up       | Required action (PR4 fix, none, etc.)                  |

## Resolved regressions (originally flagged)

| Regression             | Intentional | User Visible | Follow-up        | Resolution                |
| ---------------------- | ----------- | ------------ | ---------------- | ------------------------- |
| Button color (Duyệt)   | No          | Yes          | Fix before merge | ✅ Fixed in commit 52ce7cc |
| Header icon (CreateCfg)| No          | Yes          | Fix before merge | ✅ Fixed in commit cfc5e4d |
| Header icon (ConfigDif)| No          | Yes          | Fix before merge | ✅ Fixed in commit a5e9a2c |

## All PR3 changes — final state

| # | Modal                | Change                                     | Intentional | User Visible | Follow-up |
| - | -------------------- | ------------------------------------------ | ----------- | ------------ | --------- |
| 1 | Create Leave Request | raw `<div role="dialog">` → shared `<Modal>` | Yes         | No           | None      |
| 2 | Detail Review Leave  | raw `<div>` → shared `<Modal>`             | Yes         | No           | None      |
| 3 | Detail Swap          | raw `<div>` → shared `<Modal>`             | Yes         | No           | None      |
| 4 | ApplyConfirmation    | inline footer → `<ModalFooter>`             | Yes         | No           | None      |
| 5 | SaveTemplate         | raw `<button>` → `<Button variant="primary">` | Yes       | No           | None      |
| 6 | ApplyTemplate        | raw `<button>` (×5) → shared `<Button>`     | Yes         | No           | None      |
| 7 | CreateConfig         | raw `<div>` → `<Modal>` + icon prop       | Yes         | No           | None      |
| 8 | ConfigDiff           | raw `<div>` → `<Modal>` + icon prop       | Yes         | No           | None      |
| 9 | ShiftDetail          | raw `<button>` (×6) → shared `<Button>`     | Yes         | No           | None      |
| 10| ConflictResolution   | raw `<button>` (×2) → shared `<Button>`     | Yes         | No           | None      |
| 11| CreateProfile        | raw `<div>` → `<Modal>`                     | Yes         | No           | None      |
| 12| ImportExport         | raw `<div>` → `<Modal>`                     | Yes         | No           | None      |
| 13| Shared Modal         | added `icon` + `iconClassName` props        | Yes         | No (opt-in)  | None      |

## Modal — pre-existing intentional changes (carry-over, not regressions)

| # | Change                                          | Reason                                      |
| - | ----------------------------------------------- | ------------------------------------------- |
| - | Footer negative-margin hack removed             | Working around inline div, not layout bug  |
| - | `animate-fade-in` / `animate-scale-in` removed  | Shared Modal uses `animate-in fade-in zoom-in-95` |
| - | `bg-surface-container-low` on header removed    | Modal header is plain `bg-surface-container-lowest` |
| - | `if (!open) return null` removed                | Modal handles `open` prop internally       |
| - | `'secondary' variant override on Duyệt preserved` | Original semantics: green = approve      |

## Test status

| Suite                        | Before PR3 | After PR3 |
| ---------------------------- | ---------- | --------- |
| ConflictResolutionModal      | 7 pass     | 7 pass    |
| QuickAddModal                | 7 pass     | 7 pass    |
| Full vitest run (356 tests)  | 12 fail (pre-existing) | 12 fail (pre-existing) |
| Next.js build                | PASS       | PASS      |
| TypeScript                   | 0 errors   | 0 errors  |

The 12 pre-existing test failures are in `useAutoSchedule`,
`GuardedScheduleByTypePage`, and `PermissionMatrixContent` — none of these
are related to modal migration.

## Accessibility — inherited from shared Modal

| Behavior       | Code reference                  | Status      |
| -------------- | ------------------------------- | ----------- |
| Escape closes  | `Modal.tsx` line 42             | Inherited   |
| Focus trap     | `Modal.tsx` lines 43-56         | Inherited   |
| Focus restore  | `Modal.tsx` lines 22-37         | Inherited   |
| Body scroll lock | `Modal.tsx` line 62           | Inherited   |
| Overlay click  | `Modal.tsx` lines 84-85         | Inherited   |
| ARIA dialog    | `Modal.tsx` lines 74-78         | Inherited   |
| Animation      | `Modal.tsx` line 91             | Inherited   |

Every modal migrated automatically benefits from these behaviors, replacing
the inconsistent custom implementations across the codebase.

## Skipped (out of scope)

| File                        | Reason                                              | Tracking |
| --------------------------- | --------------------------------------------------- | -------- |
| `AutoCalculateDialog.tsx`   | 1700 LOC, state machine — split into 4 commits in PR4 | docs/todo/auto-calculate-dialog-refactor.md |

## Reviewer verdict

- Pre-review requests: ✅ Evidence, modal inheritance, AutoCalculateDialog
- Post-review requests: ✅ Button color revert, header icon restore
- Net regression count after fixes: **0**