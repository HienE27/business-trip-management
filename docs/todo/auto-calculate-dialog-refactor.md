# TODO(PR4) — Refactor AutoCalculateDialog

## Status

Skipped from PR3 (Modal Migration) due to size. Documented here for explicit
tracking so reviewer knows this is **not forgotten** — it is intentionally
deferred to a separate PR.

## File

- `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/AutoCalculateDialog.tsx`
- **1700+ LOC** (largest dialog in the codebase)
- **19+ raw `<button>` elements** scattered across multiple phases
- Multiple internal "pages" implemented as state machine, not separate routes

## Why not in PR3?

PR3 commits are sized so that:

- One commit = one modal
- One commit ≤ ~100 LOC diff
- Reviewer can mentally diff in <2 minutes

AutoCalculateDialog does not fit this constraint. Splitting it into more
atomic commits would still leave the underlying complexity (state machine)
within each commit. Better to refactor it as a single, deliberate effort.

## Suggested refactor plan

Break AutoCalculateDialog into 4 sub-components, each a small modal/sheet:

1. **Step1 — Algorithm selection step**
   - Currently: header + result + history + preview + detail + button cluster
   - Target: extract Step1 into a focused component

2. **Step2 — Config preview step**
   - Currently: renders JSON previews with diff highlighting
   - Target: separate concern, can be tested independently

3. **Summary — Final review step**
   - Currently: shows aggregated metrics before applying
   - Target: standalone summary dialog

4. **Result — Apply result step**
   - Currently: shows success/failure per staff
   - Target: standalone result dialog

## Required when picked up

Before starting work, verify:

- [ ] Read `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/AutoCalculateDialog.tsx` in full
- [ ] Identify all state variables and which steps use them
- [ ] Map existing tests (if any) for behavior preservation
- [ ] Use 4 separate commits, one per sub-component
- [ ] Each commit: tsc + build + existing tests pass

## Out of scope for PR4

This work is part of the larger "Modal API cleanup" effort (PR4 in the
roadmap). It can be picked up:

- After PR3 merges
- Or as a parallel PR if agenda aligns
- After PR2 (dead code cleanup) if timing allows

## Tracking

- Discovered during: PR3 (Modal Migration)
- Branch: `refactor/modal-migration`
- Reporter: code review
- Priority: medium (functional, but technical debt)
