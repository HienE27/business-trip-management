# Business Rules (Frontend)

> Mapping L01–L04, conflict, compensation ra giao diện.

---

## Shift types (mapping ra UI)

| ID | Label tiếng Việt | Icon (Material Symbols) | Màu Tailwind |
|---|---|---|---|
| `L01` | Trực 24/24 | `emergency` | `bg-red-100 border-red-500 text-red-800` |
| `L02` | Thông tầm | `schedule` | `bg-blue-100 border-blue-500 text-blue-800` |
| `L03` | Phòng khám dịch vụ | `medical_services` | `bg-orange-100 border-orange-500 text-orange-800` |
| `L04` | Phòng khám chuyên gia | `stethoscope` (hoặc `psychology`) | `bg-purple-100 border-purple-500 text-purple-800` |

---

## Constant files

### `lib/schedule-mapping.ts`

```typescript
import type { ShiftTypeId } from "@/types/api";

export const SHIFT_TYPE_INFO: Record<ShiftTypeId, {
  label: string;
  shortLabel: string;
  icon: string;
  colorClass: string;
  borderClass: string;
  textClass: string;
  hasCompensation: boolean;
}> = {
  L01: {
    label: "Lịch trực 24/24",
    shortLabel: "Trực 24/24",
    icon: "emergency",
    colorClass: "bg-red-100",
    borderClass: "border-red-500",
    textClass: "text-red-800",
    hasCompensation: true,
  },
  L02: {
    label: "Lịch thông tầm",
    shortLabel: "Thông tầm",
    icon: "schedule",
    colorClass: "bg-blue-100",
    borderClass: "border-blue-500",
    textClass: "text-blue-800",
    hasCompensation: false,
  },
  L03: {
    label: "Phòng khám dịch vụ",
    shortLabel: "PK Dịch vụ",
    icon: "medical_services",
    colorClass: "bg-orange-100",
    borderClass: "border-orange-500",
    textClass: "text-orange-800",
    hasCompensation: false,
  },
  L04: {
    label: "Phòng khám chuyên gia",
    shortLabel: "PK Chuyên gia",
    icon: "stethoscope",
    colorClass: "bg-purple-100",
    borderClass: "border-purple-500",
    textClass: "text-purple-800",
    hasCompensation: false,
  },
};

export function getShiftTypeInfo(id: string) {
  return SHIFT_TYPE_INFO[id as ShiftTypeId] ?? null;
}

export function getShiftTypeColor(id: string): string {
  const info = getShiftTypeInfo(id);
  return info ? `${info.colorClass} ${info.borderClass} ${info.textClass}` : "";
}
```

---

## Compensation day

- Hiển thị như một "shift" đặc biệt: `bg-slate-100 border-slate-400 text-slate-600`, icon `bedtime`.
- Tooltip: "Nghỉ bù sau ca trực L01 ngày YYYY-MM-DD".
- KHÔNG cho phép tạo/sửa schedule khác trùng ngày compensation.
- API sẽ trả 409 Conflict nếu vi phạm → frontend hiển thị toast lỗi.

---

## Conflict visualization

Khi backend trả `hasConflict = true` (qua `/schedules/conflicts/check`):

```typescript
const isConflicted = (schedule: Schedule) => schedule.hasConflict;

<div className={cn(
  "rounded-lg border p-3",
  isConflicted(schedule)
    ? "bg-rose-50 border-rose-500"
    : getShiftTypeColor(schedule.shiftTypeId)
)}>
  {isConflicted(schedule) && (
    <span className="material-symbols-outlined text-error">warning</span>
  )}
</div>
```

---

## Period status (Schedule Period)

| Status | Label | Cho edit? | Hiển thị với staff? |
|---|---|---|---|
| `DRAFT` | Đang soạn | ✅ Có (manager) | ❌ Ẩn khỏi dashboard |
| `PUBLISHED` | Đã công bố | ❌ Không (read-only) | ✅ Hiển thị |
| `ARCHIVED` | Lưu trữ | ❌ Không | Tab riêng "Lịch sử" |

UI: badge màu tương ứng:
- DRAFT: `bg-tertiary-fixed text-on-tertiary-fixed-variant`
- PUBLISHED: `bg-secondary-container text-on-secondary-container`
- ARCHIVED: `bg-surface-container-highest text-outline`

---

## Leave request status

| Status | Label | Action cho manager |
|---|---|---|
| `PENDING` | Chờ duyệt | Duyệt / Từ chối |
| `APPROVED` | Đã duyệt | (không) |
| `REJECTED` | Từ chối | (không) |
| `CANCELLED` | Đã hủy | (không) |

---

## Schedule exchange status

| Status | Label |
|---|---|
| `PENDING` | Chờ duyệt |
| `APPROVED` | Đã duyệt |
| `REJECTED` | Từ chối |
| `CANCELLED` | Đã hủy |

---

## Validation UX

Khi form lỗi validation:

```typescript
{fieldErrors.staffId && (
  <p className="text-error font-body-sm mt-1">{fieldErrors.staffId}</p>
)}
```

Khi form lỗi conflict (server 409):

```typescript
showToast({
  type: "error",
  message: "L01 và L02 không thể cùng ngày cho cùng nhân sự",
});
```

---

## Modal flow cho tạo schedule nhanh

```
[User click ô trống trên calendar]
  → QuickAddModal mở
  → Step 1: chọn staff (autocomplete)
  → Step 2: shift type (L01/L02/L03/L04 buttons)
  → Step 3: confirm
  → Submit → POST /schedules
  → Success: toast + đóng modal + refresh calendar
  → Error: hiển thị message lỗi trong modal
```

---

## Calendar display

- Grid 7 cột (T2 → CN).
- Mỗi ô hiển thị: date + tổng số ca trực + số conflict.
- Click vào ngày → mở danh sách chi tiết + nút "+ Thêm ca".
- Shift hiển thị theo giờ: 7h30 (L01 bắt đầu), ca khám 8h–17h.

---

## Auto-scheduling preview

Khi user chạy auto-scheduling, hiển thị modal preview trước khi apply:

```
Preview:
  Tổng số ca: 124
  Phân bổ: 8-10 ca/người
  Xung đột tiềm ẩn: 0
  
  [Tải lại] [Apply] [Hủy]
```

Component: `components/auto-scheduling/AutoSchedulePanel.tsx`.

---

## Common UI patterns

### Filter bar

```html
<div class="flex gap-3 items-center">
  <select class="h-10 px-3 border border-outline-variant rounded-lg">
    <option>Tất cả chuyên khoa</option>
  </select>
  <select class="h-10 px-3 border border-outline-variant rounded-lg">
    <option>Tất cả trạng thái</option>
  </select>
  <input class="h-10 px-3 border border-outline-variant rounded-lg" placeholder="Tìm kiếm..." />
</div>
```

### Empty state

```html
<div class="text-center py-12">
  <span class="material-symbols-outlined text-6xl text-outline">calendar_month</span>
  <h3 class="font-headline-md text-on-surface mt-3">Chưa có lịch</h3>
  <p class="font-body-sm text-on-surface-variant mt-1">Tạo kỳ lịch mới để bắt đầu</p>
  <button class="mt-4 bg-primary text-on-primary px-4 py-2 rounded-lg">+ Tạo kỳ lịch</button>
</div>
```

### Loading skeleton

```html
<div class="animate-pulse space-y-2">
  <div class="h-4 bg-surface-container rounded w-3/4"></div>
  <div class="h-4 bg-surface-container rounded w-1/2"></div>
</div>
```