# Directory Structure

> Cấu trúc thư mục `frontend/`.

---

## Tổng quan

```
frontend/
├── app/                                  # Next.js App Router
│   ├── (auth)/                           # Auth group (no sidebar)
│   │   ├── layout.tsx
│   │   └── login/
│   │       ├── page.tsx
│   │       └── LoginForm.tsx
│   ├── (dashboard)/                      # Protected group (có sidebar)
│   │   └── ...                            # pages thật nằm ở root app/, không trong group
│   ├── audit-history/page.tsx
│   ├── auto-scheduling/
│   │   ├── page.tsx
│   │   ├── algorithm-config/page.tsx
│   │   └── history/page.tsx
│   ├── dashboard/page.tsx
│   ├── expert-clinic/page.tsx
│   ├── holidays/page.tsx
│   ├── leave-requests/page.tsx
│   ├── monthly-schedule/page.tsx
│   ├── notifications/page.tsx
│   ├── reports/
│   │   ├── page.tsx
│   │   ├── conflicts/page.tsx
│   │   ├── monthly/page.tsx
│   │   └── staff/page.tsx
│   ├── settings/page.tsx
│   ├── staff/
│   │   ├── page.tsx
│   │   ├── [id]/page.tsx
│   │   ├── [id]/edit/page.tsx
│   │   ├── create/page.tsx
│   │   └── profile/page.tsx
│   ├── swap-requests/page.tsx
│   ├── error.tsx
│   ├── globals.css
│   ├── layout.tsx                        # Root layout
│   └── page.tsx                          # Redirect → /dashboard
├── components/
│   ├── auth/
│   │   ├── AuthGuard.tsx
│   │   ├── AuthProvider.tsx
│   │   └── AuthStatus.tsx
│   ├── auto-scheduling/
│   ├── dashboard/
│   ├── duty-24/
│   ├── layout/
│   │   ├── AppSidebar.tsx
│   │   ├── DashboardHeader.tsx
│   │   ├── DashboardShell.tsx
│   │   ├── HeaderWidgets.tsx
│   │   └── WorkflowShell.tsx
│   ├── monthly-schedule/                 # 1 module = 1 folder
│   │   ├── AutoSchedulePanel.tsx
│   │   ├── ConflictSection.tsx
│   │   ├── constants.ts
│   │   ├── CoverageSection.tsx
│   │   ├── KPISection.tsx
│   │   ├── QuickAddModal.tsx
│   │   ├── ReviewSnapshotPanel.tsx
│   │   ├── ScheduleCalendarSection.tsx
│   │   ├── ScheduleHeader.tsx
│   │   ├── ScheduleTabs.tsx
│   │   ├── ShiftDetailModal.tsx
│   │   ├── types.ts
│   │   ├── utils.ts
│   │   ├── WorkflowStepper.tsx
│   │   └── WorkloadPanel.tsx
│   ├── operations/
│   ├── schedule-summary/
│   ├── shift-detail/
│   ├── staff/
│   ├── ui/                                # Reusable UI primitives
│   │   ├── Badge.tsx
│   │   ├── ConflictResolutionModal.tsx
│   │   ├── EmptyState.tsx
│   │   ├── ErrorBoundary.tsx
│   │   ├── FAB.tsx
│   │   ├── KPICard.tsx
│   │   ├── Modal.tsx
│   │   ├── NotificationCenter.tsx
│   │   ├── NotificationContext.tsx
│   │   ├── Pagination.tsx
│   │   ├── SectionCard.tsx
│   │   ├── SimpleDataTable.tsx
│   │   ├── Skeleton.tsx
│   │   ├── StatCard.tsx
│   │   └── ToastProvider.tsx
│   └── ErrorBoundary.tsx
├── lib/                                  # Utilities + API client
│   ├── api.ts
│   ├── auth.ts
│   ├── date.ts
│   ├── errors.ts
│   ├── schedule.ts                       # Schedule helpers
│   └── utils.ts
├── hooks/                                # Custom React hooks
├── types/                                # TypeScript types
│   └── api.ts
├── tests/                                # E2E tests (Playwright)
├── public/
├── package.json
├── pnpm-lock.yaml
├── next.config.ts
├── tailwind.config.ts
├── tsconfig.json
├── vitest.config.ts
├── playwright.config.ts
└── server.js                             # Custom Next.js server
```

---

## Quy ước đặt tên

| Loại | Convention | Ví dụ |
|---|---|---|
| Page (route) | `app/<resource>/page.tsx` | `app/staff/page.tsx` |
| Dynamic page | `app/<resource>/[id]/page.tsx` | `app/staff/[id]/page.tsx` |
| Component | PascalCase, kebab-case cho file | `ScheduleCard.tsx` |
| Hook | camelCase với `use` prefix | `useSchedule` |
| Util | camelCase | `formatDate` |
| Type | PascalCase | `Schedule`, `ShiftType` |
| Constant | UPPER_SNAKE_CASE | `L01_COLOR_CLASS` |

---

## Quy tắc tổ chức

1. **1 module = 1 folder** trong `components/`. Ví dụ: `components/monthly-schedule/` chứa tất cả component liên quan.
2. **`components/ui/`** chỉ chứa primitive tái sử dụng (Badge, Modal, EmptyState). KHÔNG đặt domain component ở đây.
3. **Page** (`app/<resource>/page.tsx`) chỉ làm composition, delegate logic cho component con.
4. **Server vs Client Component**:
   - Mặc định Server Component (không có `"use client"`).
   - Thêm `"use client"` khi dùng `useState`, `useEffect`, `onClick`, `onChange`, browser API.
5. **TypeScript types** dùng chung đặt trong `types/api.ts`. Component-local types đặt trong file component.
6. **API client** centralize trong `lib/api.ts` — KHÔNG gọi `fetch`/`axios` trực tiếp trong component.

---

## Module = Folder

Mỗi tính năng lớn (VD: `monthly-schedule`) có folder riêng trong `components/`:

```
components/monthly-schedule/
├── ScheduleHeader.tsx           # Main view
├── ScheduleTabs.tsx
├── AutoSchedulePanel.tsx       # Sub-feature
├── ConflictSection.tsx
├── CoverageSection.tsx
├── KPISection.tsx
├── QuickAddModal.tsx
├── ReviewSnapshotPanel.tsx
├── ScheduleCalendarSection.tsx
├── ShiftDetailModal.tsx
├── WorkflowStepper.tsx
├── WorkloadPanel.tsx
├── types.ts                     # Local types
├── utils.ts                     # Local utils
└── constants.ts                 # Local constants
```

---

## Anti-patterns

| ❌ KHÔNG | ✅ DÙNG |
|---|---|
| Đặt tất cả component trong `app/` page | Tách ra `components/<module>/` |
| `fetch('/api/...')` inline trong component | Qua `lib/api.ts` |
| Hardcode color `bg-slate-50` | Dùng `bg-surface-container-lowest` |
| Inline `toLocaleDateString('vi-VN')` | Dùng `formatDate` từ `lib/date.ts` |
| 1 file component > 500 dòng | Tách sub-component |
| `useEffect` để fetch data trong production | Next.js 16: dùng Server Component + `fetch` cache |
| Logic nghiệp vụ L01–L04 inline trong UI | Đặt trong `lib/schedule.ts` hoặc `lib/schedule-mapping.ts` |
| Mỗi page tự wrap AuthGuard | Layout cha đã wrap sẵn (xem `app/layout.tsx`) |