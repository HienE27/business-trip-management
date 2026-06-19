# Styling & Design Tokens

> Quy ước Tailwind tokens cho **Hospital Scheduler** (Material-like Surface System).

---

## Design source of truth

- Folder reference: `stitch_medschedule_pro_dashboard/` (chứa `code.html` chuẩn)
- Quy ước: dùng `standardized` hoặc `1440x1024` screen
- Quy ước CSS: `bg-surface-container-lowest`, `text-on-surface`, …

**KHÔNG dùng** Tailwind mặc định `slate-*`, `gray-*`, `blue-500`, … cho semantic colors.

---

## Color tokens (Material-like)

### Primary / Secondary / Tertiary

| Token | Hex | Tailwind class | Usage |
|---|---|---|---|
| `primary` | `#004ac6` | `bg-primary text-on-primary` | Action chính, active nav, link |
| `primary-container` | `#2563eb` | `bg-primary-container` | Container accent |
| `on-primary-container` | `#eeefff` | `text-on-primary-container` | Text on container |
| `primary-fixed` | `#dbe1ff` | `bg-primary-fixed` | Subtle primary bg |
| `secondary` | `#006e2d` | `bg-secondary text-on-secondary` | Positive states |
| `secondary-container` | `#7cf994` | `bg-secondary-container` | Container green |
| `on-secondary-container` | `#007230` | `text-on-secondary-container` | Text on green |
| `tertiary` | `#973400` | `bg-tertiary text-on-tertiary` | Warning / orange-red |
| `tertiary-container` | `#c04400` | `bg-tertiary-container` | |
| `on-tertiary-container` | `#ffede7` | `text-on-tertiary-container` | |

### Surface system

| Token | Hex | Usage |
|---|---|---|
| `background` | `#f7f9fb` | Page background |
| `surface` | `#f7f9fb` | Section background |
| `surface-container-lowest` | `#ffffff` | Cards, panels |
| `surface-container-low` | `#f2f4f6` | Header bars, table headers |
| `surface-container` | `#eceef0` | Hover states |
| `surface-container-high` | `#e6e8ea` | Elevated hover |
| `surface-container-highest` | `#e0e3e5` | Highest elevation |

### Semantic

| Token | Hex | Usage |
|---|---|---|
| `error` | `#ba1a1a` | Error states |
| `error-container` | `#ffdad6` | Error background |
| `on-error-container` | `#93000a` | Text on error bg |
| `outline` | `#737686` | Default borders |
| `outline-variant` | `#c3c6d7` | Subtle borders |
| `on-surface` | `#191c1e` | Default text |
| `on-surface-variant` | `#434655` | Muted text |

---

## Schedule type colors (override surface tokens)

Dùng Tailwind `*-100` và `*-500` để giữ contrast tốt:

| Type | Class | Use case |
|---|---|---|
| L01 - Trực 24/24 | `bg-red-100 border-red-500 text-red-800` | Duty schedule |
| L02 - Thông tầm | `bg-blue-100 border-blue-500 text-blue-800` | All-day shifts |
| L03 - PK Dịch vụ | `bg-orange-100 border-orange-500 text-orange-800` | Service clinic |
| L04 - PK Chuyên gia | `bg-purple-100 border-purple-500 text-purple-800` | Expert clinic |
| Nghỉ bù | `bg-slate-100 border-slate-400 text-slate-600` | Compensation |
| Xung đột | `bg-rose-50 border-rose-500 text-rose-700` | Conflict alert |

Đặt constant trong `lib/schedule-mapping.ts`:

```typescript
export const SHIFT_TYPE_COLORS: Record<string, string> = {
  L01: "bg-red-100 border-red-500 text-red-800",
  L02: "bg-blue-100 border-blue-500 text-blue-800",
  L03: "bg-orange-100 border-orange-500 text-orange-800",
  L04: "bg-purple-100 border-purple-500 text-purple-800",
};
```

---

## Typography

Font: **Inter** (Google Fonts). Đã setup trong `app/layout.tsx` qua `next/font/google`.

| Class | Size / Weight | Usage |
|---|---|---|
| `font-display-lg text-display-lg` | 32px / 700 | Page title |
| `font-headline-lg text-headline-lg` | 24px / 600 | Section header |
| `font-headline-md text-headline-md` | 20px / 600 | Card header |
| `font-title-lg text-title-lg` | 18px / 600 | Widget title |
| `font-body-md text-body-md` | 16px / 400 | Body text |
| `font-body-sm text-body-sm` | 14px / 400 | Secondary text |
| `font-label-md text-label-md` | 13px / 500 | Table cells, nav |
| `font-label-sm text-label-sm` | 11px / 600 | Labels, uppercase |

---

## Spacing system

| Token | Value | Usage |
|---|---|---|
| `sidebar-width` | 260px | Sidebar width |
| `sidebar-collapsed` | 72px | Collapsed sidebar |
| `header-height` | 60px (h-16) | Top header |
| `footer-height` | 48px | Footer |
| `container-max` | 1440px | Max content width |
| `gutter` | 16px | Grid gap |
| `margin-desktop` | 24px (p-6) | Page padding desktop |
| `margin-mobile` | 16px (p-4) | Page padding mobile |

---

## Border radius

| Token | Value | Tailwind | Usage |
|---|---|---|---|
| Default | 4px | `rounded` | Buttons, inputs |
| LG | 8px | `rounded-lg` | Cards, panels |
| XL | 12px | `rounded-xl` | Modals, large cards |
| Full | 9999px | `rounded-full` | Avatar, badges, pills |

---

## Component patterns

### Card chuẩn

```html
<div class="bg-surface-container-lowest rounded-lg border border-outline-variant shadow-sm p-5">
  <h3 class="font-headline-md text-headline-md text-on-surface">Tiêu đề</h3>
  <p class="font-body-sm text-body-sm text-on-surface-variant">Nội dung</p>
</div>
```

### Card với accent left border

```html
<div class="bg-surface-container-lowest border-l-4 border-l-primary rounded-lg p-5">
  Nội dung
</div>
```

### Button primary

```html
<button class="bg-primary text-on-primary px-4 py-2.5 rounded-lg font-label-md
  hover:bg-primary-container transition-colors disabled:opacity-50">
  Lưu thay đổi
</button>
```

### Status badge

```html
<!-- Active / OK -->
<span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full
  bg-secondary-container text-on-secondary-container text-[12px] font-semibold">
  <span class="w-1.5 h-1.5 rounded-full bg-secondary"></span>
  Đang làm việc
</span>

<!-- Warning -->
<span class="inline-flex items-center px-3 py-1 rounded-full
  bg-tertiary-fixed text-on-tertiary-fixed-variant text-[12px] font-semibold">
  Cảnh báo
</span>

<!-- Error -->
<span class="inline-flex items-center px-3 py-1 rounded-full
  bg-error-container text-on-error-container text-[12px] font-semibold">
  Quá tải
</span>
```

### Form input

```html
<input
  class="w-full h-10 px-3 border border-outline-variant bg-surface-container-lowest
  text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20
  focus:border-primary transition-all rounded-lg"
  placeholder="Nhập tên nhân sự..."
  type="text"
/>
```

### Progress bar (workload)

```html
<div class="flex justify-between items-center mb-1">
  <span class="font-label-md text-label-md text-on-surface">BS. Nguyễn Văn A</span>
  <span class="font-label-sm text-label-sm text-error font-bold">128h</span>
</div>
<div class="w-full bg-surface-variant rounded-full h-1.5">
  <div class="bg-error h-1.5 rounded-full" style="width: 95%"></div>
</div>
```

---

## Icon mapping (Material Symbols Outlined)

| Icon name | Usage |
|---|---|
| `dashboard` | Tổng quan |
| `groups` | Nhân sự |
| `emergency` | Trực 24/24 (L01) |
| `schedule` | Thông tầm (L02) |
| `medical_services` | Phòng khám dịch vụ (L03) |
| `stethoscope` | Phòng khám chuyên gia (L04) |
| `psychology` | Chuyên gia (alt) |
| `calendar_month` | Lịch công tác |
| `auto_mode` | Auto scheduling |
| `swap_horiz` | Đổi ca |
| `assessment` | Thống kê & báo cáo |
| `history` | Nhật ký |
| `notifications` | Thông báo |
| `settings` | Cài đặt |
| `person` | Hồ sơ cá nhân |
| `add` | Thêm mới |
| `edit` | Sửa |
| `delete` | Xóa |
| `visibility` | Xem chi tiết |
| `warning` | Cảnh báo |
| `check_circle` | Thành công |
| `event_available` | Lịch đã công bố |
| `filter_list` | Bộ lọc |
| `more_vert` | Menu thêm |
| `local_hospital` | Khoa / Phòng ban |
| `chevron_left`, `chevron_right` | Điều hướng |
| `expand_more` | Dropdown |

**Cách dùng**:

```html
<span class="material-symbols-outlined">schedule</span>

<!-- Filled variant cho active state -->
<span class="material-symbols-outlined" style="font-variation-settings: 'FILL' 1;">
  notifications
</span>
```

---

## Sidebar layout

```html
<aside class="w-[260px] bg-surface-container-low border-r border-outline-variant py-4 px-3">
  <div class="px-6 mb-8 flex items-center gap-3">
    <span class="material-symbols-outlined text-primary">medical_services</span>
    <span class="font-title-lg">MedSchedule Pro</span>
  </div>
  <nav>
    <a class="flex items-center gap-3 px-4 py-3 rounded-lg bg-primary text-on-primary">
      <span class="material-symbols-outlined">dashboard</span>
      Tổng quan
    </a>
    <a class="flex items-center gap-3 px-4 py-3 rounded-lg
      text-on-surface-variant hover:bg-surface-container-high transition-colors">
      <span class="material-symbols-outlined">groups</span>
      Nhân sự
    </a>
  </nav>
</aside>
```

---

## Top header

```html
<header class="h-16 sticky top-0 z-50 bg-surface-container-lowest
  border-b border-outline-variant shadow-sm px-6 flex items-center justify-between">
  <div>
    <h1 class="font-headline-md text-headline-md text-on-surface">Lịch công tác tháng 6</h1>
  </div>
  <div class="flex items-center gap-4">
    <div class="relative">
      <span class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2">search</span>
      <input class="pl-10 pr-4 py-2 bg-surface-container-low rounded-full w-64" type="text" />
    </div>
    <button class="material-symbols-outlined">notifications</button>
    <Avatar />
  </div>
</header>
```

---

## Responsive

- **Sidebar**: `hidden md:flex` (mobile dùng hamburger menu)
- **Content**: grid collapse → single column trên mobile
- **Tables**: wrap trong `overflow-x-auto`
- **Search bar**: `w-64` desktop → `w-full` mobile
- **Top nav**: sticky trên mọi viewport
- **Padding**: `p-6` (24px) desktop → `p-4` (16px) mobile

---

## Custom scrollbar

```css
::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: #c3c6d7; border-radius: 9999px; }
::-webkit-scrollbar-thumb:hover { background: #737686; }
```

---

## Nguyên tắc khi review UI

1. **Có dùng `surface-*` token** thay vì `slate-*`, `gray-*`?
2. **Có icon Material Symbols** đúng mapping không?
3. **Vietnamese labels** giữ nguyên, không dịch?
4. **Hover state** có `transition-colors`?
5. **Form focus** có `focus:border-primary focus:ring-2 focus:ring-primary/20`?
6. **Border + shadow-sm** cho card?
7. **Active nav** có `bg-primary text-on-primary` hoặc `border-l-4 border-primary`?