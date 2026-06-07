# Frontend Design Spec — MedSchedule Pro

> Nguồn thiết kế chuẩn: `stitch_medschedule_pro_dashboard/`
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
Primary:     #004ac6   (blue — primary actions, links)
Primary Fixed: #dbe1ff  (subtle blue tint)
Secondary:   #006e2d   (green — positive, success)
Tertiary:    #973400   (orange-red — warning, emphasis)
Error:       #ba1a1a   (red — error, danger)
```

### Surface / Background

```
Background:             #f7f9fb
Surface (section bg):  #f7f9fb
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
Dịch vụ:    #c04400  (orange)
Chuyên gia: #9333ea  (purple)
```

---

## Layout

### Sidebar

```
Width: 260px (fixed)
Background: surface-container-low (#f2f4f6)
Border right: 1px outline-variant
Logo: 40x40 rounded-lg icon + brand text
Nav item height: py-3 (12px vertical padding)
Nav item spacing: gap-1 between items
```

### Top Header

```
Height: 60px (sticky top-0)
Background: surface-container-lowest or surface
Border bottom: 1px outline-variant + shadow-sm
```

### Content Canvas

```
Max width: 1440px
Padding desktop: 24px
Padding mobile: 16px
```

### Sidebar + Content Layout Pattern

```tsx
<div class="flex min-h-screen">
  {/* Sidebar: fixed, z-50, hidden md:flex */}
  <aside class="fixed left-0 top-0 h-full w-[260px] z-50 ...">

  {/* Content wrapper: ml-[260px] */}
  <div class="ml-[260px] flex-1 flex flex-col">
    {/* Sticky header */}
    <header class="sticky top-0 z-40 h-16 ...">

    {/* Main content */}
    <main class="flex-1 p-6 bg-background ...">
      <div class="max-w-[1440px] mx-auto">
        {/* page content */}
      </div>
    </main>
  </div>
</div>
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
| `label-md` | 13px / 500 | Table cell, nav item |
| `label-sm` | 11px / 600 | Label, caption, uppercase |

Font: **Inter** — always use Inter, never fallback to system fonts.

---

## Component Patterns

### Card

```html
<div class="bg-surface-container-lowest rounded-lg border border-outline-variant shadow-sm p-5">
```

### Card with left accent border

```html
<div class="bg-surface-container-lowest border-l-4 border-l-primary rounded-lg p-5">
```

### Data Table

```html
<table class="w-full text-left border-collapse">
  <thead>
    <tr class="bg-surface-container-low border-b border-outline-variant">
      <th class="py-3 px-4 font-label-sm text-label-sm text-on-surface-variant uppercase">...</th>
    </tr>
  </thead>
  <tbody class="divide-y divide-outline-variant">
    <tr class="hover:bg-surface-container-lowest transition-colors h-12">
      <td class="py-2 px-4 text-on-surface">...</td>
    </tr>
  </tbody>
</table>
```

### Status Badge

```html
<!-- OK -->
<span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full
  bg-secondary-container text-on-secondary-container text-[12px] font-semibold border border-on-secondary-container/10">
  <span class="w-1.5 h-1.5 rounded-full bg-secondary"></span> Label
</span>

<!-- Error -->
<span class="inline-flex items-center px-3 py-1 rounded-full
  bg-error-container text-on-error-container text-[12px] font-semibold border border-error/20">
  Label
</span>
```

### Form Input

```html
<input class="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest
  rounded-lg focus:border-primary focus:ring-1 focus:ring-primary/20 focus:outline-none
  text-body-sm text-on-surface transition-all" />
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
| `/audit-log` | Nhật ký thao tác | `history` | `nh_t_k_thao_t_c_*` |
| `/notifications` | Thông báo | `notifications` | `th_ng_b_o_*` |
| `/settings` | Cài đặt hệ thống | `settings` | `c_i_t_h_th_ng_*` |
| `/profile` | Hồ sơ cá nhân | `person` | `h_s_c_nh_n_*` |

---

## Schedule Type Display

| Type | Color | Border | Badge style |
|------|-------|--------|------------|
| Trực 24/24 | `text-blue-800` bg `blue-50` | left `blue-500` | pill rounded-full |
| Thông tầm | `text-green-800` bg `green-50` | left `green-500` | pill |
| PK Dịch vụ | `text-orange-800` bg `orange-50` | left `orange-500` | pill |
| PK Chuyên gia | `text-purple-800` bg `purple-50` | left `purple-500` | pill |
| Nghỉ bù | `text-slate-600` bg `slate-100` | left `slate-400` | pill |
| Xung đột | `text-rose-700` bg `rose-50` | left `rose-500` | pill |

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
