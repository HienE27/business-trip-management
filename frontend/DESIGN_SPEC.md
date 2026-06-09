# Frontend Design Spec — MedSchedule Pro

> Nguồn thiết kế chuẩn: `stitch_medschedule_pro_dashboard/clinical_operations_system/DESIGN.md`
> Xem `SCREEN_MAPPING.md` để map route → screen file.

---

## Brand

| Property | Value |
|----------|-------|
| App Name | MedSchedule Pro |
| Brand Alias | MedOps Admin |
| Brand Color | `#004ac6` (primary blue) |
| Brand Icon | `medical_services` (Material Symbols) |

---

## Color Palette

### Brand Colors

```
Primary:          #004ac6   (blue — primary actions, links)
Primary Container: #2563eb   (blue container)
Secondary:        #006e2d   (green — success, positive)
Tertiary:         #973400   (orange-red — emphasis, warning)
Error:            #ba1a1a   (red — error, danger, conflict)
```

### Surface / Background

```
Background:             #f7f9fb
Surface:                #ffffff
Surface Bright:         #ffffff
Surface Container Lowest: #ffffff  (cards, panels)
Surface Container Low:    #f2f4f6  (header bars)
Surface Container:        #eceef0  (hover)
Surface Container High:   #e6e8ea  (stronger hover)
Surface Variant:          #e0e3e5  (dividers)
```

### Borders

```
Outline:          #737686   (default border)
Outline Variant:  #c3c6d7   (subtle border, dividers)
```

### Chart Colors

```
24/24:      #2563eb  (blue)
Thông tầm:  #006e2d  (green)
Dịch vụ:    #973400  (orange-red)
Chuyên gia: #9333ea  (purple)
```

---

## Typography

| Class | Size / Weight | Usage |
|-------|--------------|-------|
| `display-lg` | 32px / 700 | Page title |
| `headline-lg` | 24px / 600 | Section heading |
| `headline-md` | 20px / 600 | Card title |
| `title-lg` | 18px / 600 | Widget title |
| `body-md` | 16px / 400 | Body |
| `body-sm` | 14px / 400 | Secondary text |
| `label-sm` | 11px / 600 | Label, caption, uppercase |

Font: **Inter** — always use Inter, never fallback to system fonts.

---

## Layout

### Sidebar

```
Width: 260px (fixed)
Background: surface-container (#eceef0)
Border right: 1px outline (#737686)
Logo: 40x40 rounded-lg icon + brand text
Nav item height: py-3 (12px vertical padding)
Nav item spacing: gap-1 between items
Active item: bg #004ac6/10 text-primary
Hover item: bg surface-hover (#f1f5f9)
```

### Top Header

```
Height: 60px (sticky top-0)
Background: surface-container-low (#f2f4f6)
Border bottom: 1px outline-variant + shadow: 0 1px 3px rgba(0,0,0,0.1)
```

### Content Canvas

```
Max width: 1440px
Padding desktop: 24px
Padding mobile: 16px
Background: background (#f7f9fb)
```

### Sidebar + Content Layout Pattern

```tsx
<div className="flex min-h-screen">
  {/* Sidebar: fixed, z-50, hidden md:flex */}
  <aside className="fixed left-0 top-0 h-full w-[260px] z-50 ...">

  {/* Content wrapper: ml-[260px] */}
  <div className="ml-[260px] flex-1 flex flex-col">
    {/* Sticky header */}
    <header className="sticky top-0 z-40 h-16 ...">

    {/* Main content */}
    <main className="flex-1 p-6 bg-background ...">
      <div className="max-w-[1440px] mx-auto">
        {/* page content */}
      </div>
    </main>
  </div>
</div>
```

---

## Component Patterns

### Card

```html
<div className="bg-surface-container-lowest rounded-xl border border-outline
                shadow-[0_1px_3px_rgba(0,0,0,0.1)] p-4">
```

### Card with left accent border

```html
<div className="bg-surface rounded-xl border border-outline shadow-sm p-4
                border-l-4 border-l-primary">
```

### Data Table

```html
<table className="w-full text-left border-collapse">
  <thead>
    <tr className="bg-surface-container border-b border-outline">
      <th className="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">...</th>
    </tr>
  </thead>
  <tbody className="divide-y divide-outline">
    <tr className="hover:bg-surface-hover transition-colors h-12">
      <td className="py-3 px-4 text-on-surface">...</td>
    </tr>
  </tbody>
</table>
```

### Status Badge

```html
<!-- OK / Success -->
<span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full
  bg-secondary-container text-on-secondary-container text-[12px] font-semibold
  border border-secondary/20">
  <span className="w-1.5 h-1.5 rounded-full bg-secondary"></span> Label
</span>

<!-- Error / Warning -->
<span className="inline-flex items-center px-3 py-1 rounded-full
  bg-error-container text-on-error-container text-[12px] font-semibold
  border border-error/20">
  Label
</span>
```

### Form Input

```html
<input className="w-full h-10 px-3 border border-outline bg-surface
  rounded-lg focus:border-primary focus:ring-2 focus:ring-primary/20 focus:outline-none
  text-body-sm text-on-surface transition-all" />
```

### Icon Button

```html
<button className="flex h-10 w-10 items-center justify-center rounded-full
  text-on-surface-variant hover:bg-surface-hover transition-colors">
  <span className="material-symbols-outlined text-[20px]">icon</span>
</button>
```

### Primary Button

```html
<button className="h-10 px-4 bg-primary text-on-primary rounded-md font-label-md
  flex items-center gap-2 hover:brightness-110 transition-colors shadow-sm">
  Button
</button>
```

### Secondary Button

```html
<button className="h-10 px-4 border border-primary text-primary bg-transparent
  rounded-md font-label-md flex items-center gap-2 hover:bg-primary/5 transition-colors">
  Button
</button>
```

---

## Navigation Items

| Route | Label | Icon | Stitch File |
|-------|-------|------|-------------|
| `/` | Tổng quan / Bảng điều khiển | `dashboard` | `t_ng_quan_*` |
| `/staff` | Nhân sự / Quản lý nhân sự | `groups` | `danh_s_ch_nh_n_s_*` |
| `/duty-24` | Trực 24/24 | `emergency` | `l_ch_tr_c_24_24_*` |
| `/all-day` | Thông tầm | `schedule` | `*` |
| `/service-clinic` | PK Dịch vụ | `medical_services` | `*` |
| `/expert-clinic` | PK Chuyên gia | `stethoscope` | `*` |
| `/schedule-summary` | Tổng hợp lịch | `calendar_month` | `t_ng_h_p_l_ch_*` |
| `/auto-scheduling` | Tự động xếp lịch | `auto_mode` | `*` |
| `/swap-requests` | Yêu cầu đổi trực | `swap_horiz` | `g_i_y_u_c_u_i_tr_c_*` |
| `/reports` | Thống kê & Báo cáo | `assessment` | `th_ng_k_b_o_c_o_*` |
| `/notifications` | Thông báo | `notifications` | `th_ng_b_o_*` |
| `/settings` | Cài đặt hệ thống | `settings` | `c_i_t_h_th_ng_*` |
| `/staff/profile` | Hồ sơ cá nhân | `person` | `h_s_c_nh_n_*` |

---

## Schedule Type Display

| Type | Color | Border | Badge style |
|------|-------|--------|------------|
| Trực 24/24 | `text-primary` bg `primary/10` | left `border-primary` | pill rounded-full |
| Thông tầm | `text-secondary` bg `secondary/10` | left `border-secondary` | pill |
| PK Dịch vụ | `text-tertiary` bg `tertiary/10` | left `border-tertiary` | pill |
| PK Chuyên gia | `text-expert` bg `expert/10` | left `border-expert` | pill |
| Nghỉ bù | `text-slate-600` bg `slate-100` | left `border-slate-400` | pill |
| Xung đột | `text-error` bg `error/10` | left `border-error` | pill |

---

## Tech Stack

| Layer | Tech |
|-------|------|
| Framework | Next.js (App Router) |
| Package Manager | pnpm |
| CSS | Tailwind CSS v4 (with `@theme` in globals.css) |
| Icons | Material Symbols Outlined (Google Fonts) |
| Font | Inter (Google Fonts) |
| Auth | JWT (localStorage) |
| API Client | Axios-based in `lib/api-client.ts` |

### Tailwind v4 Config

Dự án dùng Tailwind CSS v4 — KHÔNG có `tailwind.config.ts`.
Thay vào đó, define theme bằng `@theme` trong `globals.css`:

```css
@import "tailwindcss";

@theme {
  --color-primary: #004ac6;
  --color-secondary: #006e2d;
  /* ... */
}
```

**Khi thêm màu mới**, chỉ cần thêm vào `@theme {}` trong `globals.css`.
**KHÔNG tạo file `tailwind.config.ts` mới** trừ khi cần config đặc biệt (plugins, content paths).

---

## Implementation Priority

Khi refactor UI theo Stitch:

1. **globals.css** — cập nhật `@theme` với đầy đủ surface tokens
2. **DashboardShell** — cập nhật layout pattern (sidebar + sticky header)
3. **AppSidebar** — cập nhật nav styling theo Stitch
4. **DashboardHeader** — cập nhật top bar
5. **Page-level components** — dashboard cards, tables, forms
6. **Login page** — cập nhật theo `ng_nh_p_*` pattern
7. **New pages** — notifications, settings, audit log
