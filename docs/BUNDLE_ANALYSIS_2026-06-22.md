# Bundle Analysis — Hospital Scheduler Frontend

**Date:** 2026-06-22
**Build:** `pnpm build` (Next.js 16.2.6, React 19.2.4)
**Note:** Post-lazy-load refactor. All 6 modal sub-components in `auto-scheduling/page.tsx`
are now code-split via `next/dynamic`. See `docs/ACCESSIBILITY_2026-06-20.md` §8
for what changed.

---

## Routes (30 total — all static)

All 30 routes are fully static (○ = static, ● = dynamic):

```
○ /
○ /_not-found
○ /all-day
○ /audit-history
○ /auto-scheduling
○ /auto-scheduling/algorithm-config
○ /auto-scheduling/history
○ /dashboard
○ /duty-24
○ /expert-clinic
○ /holidays
○ /leave-requests
○ /login
○ /monthly-schedule
○ /notifications
○ /periods
○ /reports
○ /reports/conflicts
○ /reports/monthly
○ /reports/staff
○ /requirements
○ /service-clinic
○ /settings
○ /settings/roles
○ /staff
○ /staff/create
○ /staff/profile
○ /swap-requests
```

---

## Key Shared Chunks

| Chunk | Approx size | Contains |
|-------|-------------|----------|
| `0e.*.js` | 222 KB | React + Next.js runtime |
| `0h.*.js` | 134 KB | Shared UI components (Button, Modal, etc.) |
| `03.*.js` | 110 KB | Date/time utilities, schedule types |
| `0c.*.js` | 96 KB | Chart libraries (Recharts?) |
| `0r.*.js` | 56 KB | API client |
| `02.*.js` | 53 KB | Auth context + role hooks |

---

## Notes

- **auto-scheduling/page.tsx**: ~1399 lines → now loads 6 modals via `next/dynamic`.
  Modal sub-components (`ApplyConfirmationModal`, `SaveTemplateModal`, `SuggestionsModal`,
  `ApplyTemplateModal`, `BulkPublishModal`) are NOT in the initial bundle.
- All schedule pages (`duty-24`, `all-day`, `service-clinic`, `expert-clinic`) share
  the `ScheduleByTypePage` component which was refactored DRY (saves ~700 LOC).
- Charts are lazy-loaded below the fold (WorkloadChart, AlgorithmBalanceChart).
- All routes are static — no SSR overhead for initial page load.

---

## Performance Recommendations

1. **Largest shared chunk (222 KB)** — React + Next.js runtime. This is
   unavoidable; Next.js requires it.
2. **Chart library (96 KB)** — Verify only loaded on `/reports/*` pages.
3. **Modal sub-components** — code-split via `dynamic()`. Confirm
   modal JS only downloads when user opens the modal (check Network tab).

---

## How to Re-run

```bash
cd frontend
ANALYZE=true pnpm build
# or use the script in package.json:
pnpm analyze
```

The `ANALYZE=true` flag enables `@next/bundle-analyzer` which produces
interactive HTML reports at `.next/analyze/`.
