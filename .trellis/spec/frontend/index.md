# Frontend Development Guidelines

> Quy ước phát triển frontend Next.js 16 / React 19 / Tailwind 4 cho **Hospital Scheduler**.

---

## Tech Stack (đã chốt)

| Layer | Library | Ghi chú |
|---|---|---|
| Framework | Next.js `16.2.6` (App Router) | React Server Components + Client Components |
| UI Runtime | React `19.2.4` | `useState`, `useEffect`, hooks mới |
| Styling | Tailwind CSS `4` + PostCSS | `bg-surface-container-lowest`, … (KHÔNG dùng `bg-slate-*`, `bg-gray-*`) |
| Package Manager | `pnpm` | `pnpm-lock.yaml` đã có — KHÔNG dùng npm/yarn |
| Lint | ESLint `9` với `eslint-config-next` | |
| Test | Vitest (`test:unit`) + Playwright (`test:e2e`) | |
| Icons | **Material Symbols Outlined** | Qua Google Fonts `<link>` (KHÔNG dùng Lucide, Heroicons) |
| Font | Inter (Google Fonts) | Body + UI text |
| HTTP | `axios` wrapper trong `@/lib/api` | Tự handle auth header, error |
| State | React Context (`AuthProvider`, `NotificationContext`, `ToastProvider`) | |
| Form | Native form + state hooks | (chưa dùng React Hook Form + Zod) |
| Lint/Format | `eslint-config-next` | Prettier qua IDE |

> Tham chiếu design system đầy đủ: `.cursor/rules/FRONTEND_UI_SYSTEM.mdc`.

---

## Guidelines Index

| Layer | File | Mô tả | Trạng thái |
|---|---|---|---|
| Directory Structure | [directory-structure/index.md](./directory-structure/index.md) | Tổ chức `app/`, `components/`, `lib/`, … | ✅ Filled |
| Components | [components/index.md](./components/index.md) | Naming, structure, props, khi nào client vs server | ✅ Filled |
| Styling | [styling/index.md](./styling/index.md) | Color tokens, typography, spacing | ✅ Filled |
| API | [api/index.md](./api/index.md) | `lib/api`, error handling, optimistic updates | ✅ Filled |
| State | [state/index.md](./state/index.md) | Context providers, hooks pattern | ✅ Filled |
| Testing | [testing/index.md](./testing/index.md) | Vitest unit, Playwright e2e | ✅ Filled |
| Business Rules (FE) | [business-rules-fe/index.md](./business-rules-fe/index.md) | Mapping L01–L04 ra UI | ✅ Filled |

---

## Quy trình thay đổi spec

| Khi nào | Hành động |
|---|---|
| Tạo component mới tái sử dụng | Thêm vào `components/ui/` + cập nhật component-patterns |
| Đổi color token | Cập nhật styling-tokens + kiểm tra `tailwind.config` / `globals.css` |
| Đổi API endpoint | Cập nhật api-integration + kiểm tra tất cả page gọi |
| Thêm route mới | Cập nhật directory-structure (app/) |
| Đổi business rule hiển thị | Cập nhật business-rules-fe |

---

## Lưu ý cho AI agents

- **Tailwind tokens** — dùng `bg-surface-container-lowest`, KHÔNG `bg-white` hay `bg-slate-50`.
- **Icons** — Material Symbols Outlined: `<span className="material-symbols-outlined">schedule</span>`. KHÔNG dùng emoji.
- **Vietnamese labels** — giữ nguyên tiếng Việt, không dịch.
- **Server vs Client** — default Server Component, chỉ thêm `"use client"` khi dùng hooks/interaction.
- **Auth check** — page trong `app/(dashboard)/` tự wrap bởi `AuthGuard` qua `app/layout.tsx`. KHÔNG check auth manually.
- **API errors** — dùng `getErrorMessage(err)` từ `@/lib/errors`, KHÔNG `err.message` raw.
- **Date format** — dùng `formatDate` / `formatDateFull` từ `@/lib/date`, KHÔNG `toLocaleDateString()` inline.
- **Schedule colors**:
  - L01 → đỏ (`bg-red-100 border-red-500`)
  - L02 → xanh dương (`bg-blue-100 border-blue-500`)
  - L03 → xanh lá (`bg-green-100 border-green-500`)
  - L04 → tím (`bg-purple-100 border-purple-500`)
  - Compensation → slate (`bg-slate-100 border-slate-400`)
  - Conflict → rose (`bg-rose-50 border-rose-500`)

---

**Ngôn ngữ tài liệu**: Tiếng Việt.