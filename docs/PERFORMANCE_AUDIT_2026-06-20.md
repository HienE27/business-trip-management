# Frontend Performance Audit — 2026-06-20

> Snapshot từ Next.js 16 production build với `@next/bundle-analyzer` (`pnpm analyze`).
> Reports HTML đầy đủ tại `frontend/.next/analyze/{client,edge,nodejs}.html`.

## Build Summary

| Metric | Value |
| --- | --- |
| Next.js | 16.2.6 (Turbopack default, Webpack via `--webpack` cho analyzer) |
| Routes | 30 total — 27 static (`○`), 3 dynamic (`ƒ`: `/staff/[id]`, `/staff/[id]/edit`) |
| Build time | 2.3 s (Turbopack) / 13.3 s (Webpack) |
| Static generation | 30/30 in 324 ms (19 workers) |
| TypeScript check | OK với Turbopack; Webpack mode gặp known issue với `Loading` field trong generated types — không ảnh hưởng runtime, đã verify compiled output. |

## Top Framework Bundles (vendor, base)

| Rank | Size (bytes) | KB | Note |
| --- | --- | --- | --- |
| 1 | 861,685 | 842 | Largest vendor chunk (React + Next runtime) |
| 2 | 574,367 | 561 | Vendor chunk 2 |
| 3 | 382,196 | 373 | Vendor chunk 3 |
| 4 | 92,145 | 90 | Vendor chunk 4 (small shared) |

Tổng 4 vendor chunks ≈ **1.87 MB**. Đây là React 19 + Next 16 baseline, không giảm được trừ khi nâng cấp Next.

## Top Application Chunks

| Rank | Chunk | Bytes | KB | Note |
| --- | --- | --- | --- | --- |
| 1 | `src/components/monthly-schedule` | 57,613 + 38,243 | ~94 | Lớn nhất trong app — calendar grid logic + conflict viewer |
| 2 | `src/app/staff/profile` | 60,620 | 59 | Profile page với schedule list, KPI cards |
| 3 | `src/app/reports/staff` | 45,396 | 44 | Report table + chart helpers |
| 4 | `src/app/periods` | 35,935 | 35 | Period CRUD form + filter logic |
| 5 | `src/app/holidays` | 31,832 | 31 | Holiday list/edit form |
| 6 | `src/app/reports/conflicts` | 28,026 | 27 | Conflict list + filter chips |

Tất cả trang khác dưới 25 KB mỗi cái (login 3 KB, dashboard < 15 KB).

## Optimizations Applied

### 1. `@next/bundle-analyzer` + `pnpm analyze`

```json
"scripts": {
  "analyze": "ANALYZE=true next build --webpack"
}
```

Tạo 3 reports (`client.html`, `edge.html`, `nodejs.html`) cho 3 environments.

### 2. `optimizePackageImports` (experimental) — VERIFIED

```ts
experimental: {
  // Barrel-exported paths. optimizePackageImports turns named imports
  // from these modules into deep imports at build time so unused
  // exports don't bloat the client bundle.
  optimizePackageImports: [
    "@/components/ui",         // barrel at src/components/ui/index.ts
  ],
},
```

**Verification (build: 2026-06-20)**:
- `periods/page.tsx` imports `Button` only → page.js is 986 B; **no
  ToastProvider/FormInput/ConfirmDialog/ThemeToggle found in any
  chunk loaded by /periods**.
- 3 chunks contain `FormInput + FormSelect` (login, schedule-calendar-
  widget, monthly-schedule) — those are the actual consumers.
- `ToastProvider` only loaded into 2 shared chunks (41 KB + 27 KB) and
  only fetched by routes that actually call `useToast()`.
- `ConfirmDialog` not bundled in any chunk — no current consumer.

**Note**: `@/components/schedule` was removed from the list because no
barrel file exists at that path. Adding it to optimizePackageImports
silently no-ops.

### 3. `output: 'standalone'` (đã có từ trước)

Build ra Dockerfile-friendly output cho deployment.

### 4. `next/dynamic` on bottom info panels (2026-06-20 session 3)

`ConflictSection`, `CoverageSection`, `ReviewSnapshotPanel` on
`/monthly-schedule` lazy-loaded via `next/dynamic({ ssr: false })`.
Reduces initial JS for `/monthly-schedule` from ~95 KB to ~82 KB
(panels load asynchronously after calendar paints). See commit
`483775a` for full numbers.

### 5. `RoleGuard` coverage (2026-06-20 session 1-3)

20 of 24 protected routes now route through `<RoleGuard>` (or
`<GuardedScheduleByTypePage>` for the 4 schedule-by-type routes).
Full matrix in `docs/ROLE_MATRIX_2026-06-20.md`.

## Optimization Opportunities (Priority)

### High

- [ ] **Lazy-load `monthly-schedule` chunk (~94 KB)** — lớn nhất trong app. Tách:
  - `MonthlyScheduleGrid` (core grid, ~40 KB)
  - `ConflictViewer` (only when `?panel=conflicts`)
  - `SummaryPanel` (only when `?panel=summary`)
  - Dùng `next/dynamic` với `ssr: false` cho phần ít critical.
- [ ] **Move `@stomp/stompjs` khỏi devDeps** — package không nằm trong `dependencies`, chỉ trong `devDependencies`. Đã đúng nhưng đảm bảo nó không bị kéo vào client bundle.

### Medium

- [ ] **`staff/profile` (59 KB)** — nhiều tính năng (KPI, schedule list, charts). Tách `ScheduleTimeline` thành dynamic import.
- [ ] **`reports/staff` (44 KB)** — chart helpers có thể dynamic.
- [ ] **Audit-history export button** — có thể lazy-load `xlsx` (nếu chưa thêm) hoặc dùng native CSV.

### Low

- [ ] **`periods` (35 KB) + `holidays` (31 KB) + `reports/conflicts` (27 KB)** — đã tốt. Chỉ split khi thêm features.
- [ ] **`login` (3 KB)** — không cần optimize.

## Notes & Caveats

1. **Turbopack build không tương thích với `@next/bundle-analyzer`** — analyzer yêu cầu Webpack stats. Dùng `pnpm analyze` (script đã thêm) để chuyển sang `--webpack`.
2. **Webpack build fail TS check** trên Next 16 generated types (file `.next/types/app/page.ts` complain về `Loading` field). Đây là known issue Next 16 + custom app router config; compile output đã OK, runtime không bị ảnh hưởng.
3. **App is React Server Components-friendly** — 27/30 routes là static prerendered (`○`), 3 dynamic routes (`ƒ`) do `[id]` params. Đây là baseline rất tốt cho first-paint.
4. **Image optimization** — chưa thấy dùng `next/image` trong code review nhanh. Có thể là optimization nếu có logo/avatar hiển thị.

## Recommended Next Steps

1. **Thêm `next/dynamic` cho `MonthlyScheduleGrid`** — tiết kiệm ~40 KB trên routes không phải `/monthly-schedule` (≈ 29/30 routes).
2. **Verify `optimizePackageImports` hoạt động đúng** — check bundle sau config xem `@/components/ui` barrel có thực sự được tree-shake.
3. **Profile LCP/CLS thực tế trên `/monthly-schedule`** với Lighthouse sau khi áp lazy load.
4. **Cân nhắc chuyển `@stomp/stompjs` thành peer dep nếu có nhiều clients** — không vấn đề ngay.

## Reproducing

```bash
cd frontend
pnpm analyze
# Open frontend/.next/analyze/client.html in browser
```

Reports được lưu ở:
- `frontend/.next/analyze/client.html` (446 KB) — browser bundle, mọi chunks tải về client
- `frontend/.next/analyze/edge.html` (275 KB) — Edge runtime chunks
- `frontend/.next/analyze/nodejs.html` (531 KB) — Node.js server runtime chunks