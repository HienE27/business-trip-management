# Component Patterns

> Quy ước viết component React trong dự án.

---

## Server vs Client Component

| Use case | Type | Lý do |
|---|---|---|
| Hiển thị data tĩnh (page info) | **Server** | SEO + performance |
| Form với state | **Client** (`"use client"`) | Cần `useState` |
| Có `onClick` / `onChange` | **Client** | Cần event handlers |
| Dùng `useEffect` (fetch, subscription) | **Client** | Cần browser API |
| Import component khác đã là Client | **Client** | Next.js bắt buộc |
| Đọc `localStorage` / `sessionStorage` | **Client** | Browser-only API |
| `useAuth()` / `useNotification()` | **Client** | Custom hooks có state |

**Mặc định**: Server Component. Chỉ thêm `"use client"` khi thật sự cần.

---

## Naming convention

| Loại | Quy ước | Ví dụ |
|---|---|---|
| Component | PascalCase | `ScheduleCard`, `MonthDateGrid` |
| Props type | `<Name>Props` | `ScheduleCardProps` |
| File | PascalCase, đuôi `.tsx` | `ScheduleCard.tsx` |
| Folder | kebab-case | `monthly-schedule/` |
| Hook | camelCase + `use` prefix | `useScheduleList` |
| Local type | PascalCase | `type ExchangeStatus = ...` |

---

## Component structure

```typescript
// components/monthly-schedule/ScheduleCard.tsx
"use client"; // chỉ khi cần

import { useState } from "react";
import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/Badge";

interface ScheduleCardProps {
  className?: string;
  schedule: Schedule;
  onClick?: () => void;
  showStaffName?: boolean;
}

export function ScheduleCard({
  className,
  schedule,
  onClick,
  showStaffName = true,
}: ScheduleCardProps) {
  const [isHover, setIsHover] = useState(false);

  return (
    <div
      className={cn(
        "rounded-lg border p-3 transition-colors",
        "hover:bg-surface-container-low cursor-pointer",
        className
      )}
      onClick={onClick}
      onMouseEnter={() => setIsHover(true)}
      onMouseLeave={() => setIsHover(false)}
    >
      <Badge variant={getShiftBadgeVariant(schedule.shiftTypeId)}>
        {schedule.shiftTypeId}
      </Badge>
      {showStaffName && <p className="font-body-md">{schedule.staff.fullName}</p>}
    </div>
  );
}
```

**Quy tắc**:
1. **Named export**, không default (trừ `page.tsx`, `layout.tsx` của Next.js bắt buộc default).
2. Props interface đặt ngay trên component.
3. Optional props có default value qua destructuring.
4. Dùng `cn()` từ `@/lib/utils` để merge className.
5. Logic phức tạp → tách ra `utils.ts` cùng folder hoặc `lib/`.

---

## Composition over props

Khi component có nhiều biến thể, **KHÔNG** dùng boolean prop:

```typescript
// ❌ BAD
<Card isCompact isOutlined hasIcon isClickable />

// ✅ GOOD
<Card>
  <Card.Header>
    <Card.Icon>...</Card.Icon>
    <Card.Title>...</Card.Title>
  </Card.Header>
  <Card.Body>...</Card.Body>
</Card>
```

Hoặc dùng variant:

```typescript
// ✅ OK
<Badge variant="primary" size="sm">L01</Badge>
<Badge variant="error" size="md">Conflict</Badge>
```

Xem thêm: Vercel composition patterns trong `.agents/skills/vercel-composition-patterns/SKILL.md`.

---

## Forms

```typescript
"use client";

import { useState, FormEvent } from "react";

export function CreateScheduleForm() {
  const [form, setForm] = useState({ staffId: "", date: "", shiftType: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      await api.post("/schedules", form);
      // success handling
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {/* fields */}
      {error && <p className="text-error font-body-sm">{error}</p>}
      <button
        type="submit"
        disabled={submitting}
        className="bg-primary text-on-primary px-4 py-2 rounded-lg"
      >
        {submitting ? "Đang lưu..." : "Lưu"}
      </button>
    </form>
  );
}
```

**Quy tắc form**:
- Disabled submit khi `submitting === true`.
- Hiển thị lỗi qua `text-error`, KHÔNG `alert()`.
- Reset form sau khi submit thành công.
- Validation cơ bản (required, format) làm client-side; business rule do backend.

---

## Modals / Dialogs

Dùng `components/ui/Modal.tsx`:

```typescript
import { Modal } from "@/components/ui/Modal";

<Modal open={isOpen} onClose={() => setIsOpen(false)} title="Chi tiết ca trực">
  <ShiftDetailContent shift={shift} />
</Modal>
```

KHÔNG tự build modal với raw `<div>` + Tailwind. Dùng primitive có sẵn.

---

## Data fetching

**Khuyến nghị**: Server Component cho initial load (xem `lib/api.ts`):

```typescript
// app/staff/page.tsx — Server Component
import { api } from "@/lib/api";
import { StaffList } from "@/components/staff/StaffList";

export default async function StaffPage() {
  const initialData = await api.get<StaffListResponse>("/staff");
  return <StaffList initialData={initialData} />;
}
```

**Client-side fetch** khi cần revalidate (VD: sau khi tạo mới):

```typescript
"use client";
import { useState, useEffect } from "react";

export function StaffList({ initialData }: { initialData: StaffListResponse }) {
  const [data, setData] = useState(initialData);

  const refresh = async () => {
    const fresh = await api.get<StaffListResponse>("/staff");
    setData(fresh);
  };

  return (
    <>
      <button onClick={refresh}>Refresh</button>
      {/* render data */}
    </>
  );
}
```

---

## Error handling trong component

```typescript
import { ErrorBoundary } from "@/components/ErrorBoundary";

<ErrorBoundary fallback={<ErrorState />}>
  <ScheduleCalendar />
</ErrorBoundary>
```

Mỗi page quan trọng nên wrap trong `ErrorBoundary`.

---

## Performance

- **Memo** component nặng: `React.memo(ScheduleCalendar)`.
- **useMemo** cho derived data: `const sorted = useMemo(() => sortBy(data, 'date'), [data])`.
- **useCallback** cho handler truyền vào memoized child.
- **Dynamic import** cho component nặng:
  ```typescript
  const HeavyChart = dynamic(() => import("./HeavyChart"), { ssr: false });
  ```
- **Image**: dùng `next/image` thay vì `<img>`.

---

## Tách component lớn (Phase C pattern)

Khi 1 component > 800 dòng, **TÁCH** thành sub-component trong folder con. Quy tắc:

### Quy tắc

1. **Tạo folder** `<ComponentName>/` cùng cấp với file gốc.
2. **Tách constants, types, helpers** → `<ComponentName>/constants.ts`.
3. **Tách pure logic** (build, transform, compute) → `<ComponentName>/<logicName>.ts` (không có JSX, dễ test).
4. **Tách sub-component** nhỏ → `<ComponentName>/<SubName>.tsx`.
5. **File gốc** → chỉ làm orchestration (route state, compose sub-components).
6. **Mỗi file mới < 200 dòng** trừ khi có lý do chính đáng.

### Ví dụ: `DashboardCalendar` (1562 dòng) → folder `calendar/`

```
components/dashboard/
├── DashboardCalendar.tsx          (giữ tên để không break import, nhưng 981 dòng)
└── calendar/
    ├── constants.ts               (TONE, SHIFT_SHORT, types, helpers)
    ├── buildCalendar.ts           (logic build grid, không có React)
    ├── buildCalendar.test.ts      (vitest, 12 tests)
    ├── MobileHint.tsx             (~35 dòng)
    ├── EventTooltip.tsx           (~125 dòng)
    ├── OverflowPopover.tsx        (~93 dòng)
    └── CalendarToolbar.tsx        (~115 dòng)
```

### Khi nào KHÔNG cần tách

- Component < 300 dòng và không có logic phức tạp.
- Component chỉ dùng 1 lần, không có ý định tái sử dụng.
- Component được export và dùng bởi nhiều file khác nhau ở các vai trò rất khác nhau (cần giữ API ổn định).

### Lợi ích

- Test riêng phần logic (`buildCalendar.test.ts` không cần render React).
- Debug dễ — biết bug nằm ở file nào.
- Code review dễ — file nhỏ dễ hiểu hơn file 1500 dòng.
- Tái sử dụng sub-component ở chỗ khác.

---

## Config-driven shared page (DRY pattern cho nhiều route cùng pattern)

> Khi nhiều page chỉ khác nhau vài string (title, icon, color, label) và 1-2 endpoint, **KHÔNG** duplicate boilerplate. Tạo 1 shared component với config object, mỗi page chỉ cần wrapper ~20 LOC truyền config.

### Use case

Áp dụng khi ≥ 3 page có cùng skeleton:

```
[period selector] + [optional extra filter] + [KPI grid] +
[empty/loading/calendar] + [add modal] + [detail modal]
```

Mỗi trang chỉ khác:
- Sidebar section key
- Shift type id (L01/L02/L03/L04)
- Title, description, CTA label, empty message
- 1 endpoint override (vd expert-clinic cần `/schedules/expert-clinic` thay vì `/schedules/period/{id}`)
- 1-2 boolean toggle (vd có cần compensation day? có cần detail modal?)

### Pattern (xem `ScheduleByTypePage`)

```typescript
// components/monthly-schedule/ScheduleByTypePage.tsx
export type ScheduleTypeConfig = {
  activeSection: 'duty-24' | 'all-day' | 'service-clinic' | 'expert-clinic';
  shiftTypeId: 'L01' | 'L02' | 'L03' | 'L04';
  title: string;
  description: string;
  emptyMessage: string;
  emptyIcon: string;
  ctaIcon: string;
  ctaLabel: string;
  totalShiftLabel: string;
  totalShiftAccent: string;
  staffAccent: string;
  fetchErrorMessage: string;
  compDescription: string;
  // Optional discriminator for endpoint/UI differences:
  expertClinicMode?: boolean;
};

export function ScheduleByTypePage({ config }: { config: ScheduleTypeConfig }) {
  // ... full page logic, branches on `config.expertClinicMode`
}
```

```typescript
// app/duty-24/page.tsx — chỉ 23 LOC
import { ScheduleByTypePage, type ScheduleTypeConfig } from "@/components/monthly-schedule/ScheduleByTypePage";

const config: ScheduleTypeConfig = {
  activeSection: 'duty-24',
  shiftTypeId: 'L01',
  title: 'Lịch trực 24/24',
  // ... 12 fields
};

export default function Duty24Page() {
  return <ScheduleByTypePage config={config} />;
}
```

### Khi nào KHÔNG dùng

- Các page có flow khác hẳn nhau (vd login, settings, dashboard)
- Page có > 3 điểm khác biệt đáng kể → cân nhắc tách thành 2-3 shared component thay vì 1 config-driven mega-component
- Khi config trở nên quá phức tạp (> 15 fields, 5+ boolean flags) → tách lại

### Khi thêm page mới cùng pattern

1. Copy wrapper từ page gần nhất (duty-24 → all-day)
2. Override các field string khác (title, label, icon)
3. Nếu cần endpoint riêng → thêm 1 boolean flag vào config, KHÔNG tạo component mới
4. Viết test cho page mới (theo pattern `ScheduleByTypePage.test.tsx`)

### Khi thêm endpoint override

- Thêm field `xxxMode?: boolean` vào config (KHÔNG truyền function/callback)
- Trong shared component, branch trên flag để chọn endpoint/UI
- Nếu 3+ mode xuất hiện → refactor thành strategy pattern (mỗi mode là 1 hook)

### Lợi ích

- 4 page × 226 LOC → 4 page × 23 LOC + 1 shared ~390 LOC (net ~700 LOC saved)
- Onboarding page mới chỉ cần 5 phút (copy config + đổi string)
- Test 1 nơi (`ScheduleByTypePage.test.tsx`) cover behavior chung, dễ regression check
- Design system enforcement tự động (vì tất cả page dùng chung component)

---
- **Link**: dùng `next/link` thay vì `<a href>`.

---

## Accessibility (A11y)

- Mọi `<button>` phải có `aria-label` nếu chỉ có icon.
- Form input có `<label htmlFor>` tương ứng.
- Bảng có `<th scope="col">` rõ ràng.
- Color contrast đạt WCAG AA (token của design system đã đạt).
- Modal có `role="dialog"` + `aria-modal="true"` (xem `Modal.tsx`).
- Focus trap khi mở modal.

---

## Anti-patterns

| ❌ KHÔNG | ✅ DÙNG |
|---|---|
| `useEffect` để fetch initial data trong page | Server Component + `await api.get()` |
| `any` type | Dùng `unknown` + type guard, hoặc khai báo type đúng |
| Inline function trong JSX lặp lại | Tách ra `handleX` ở trên |
| Class component | Functional component + hooks |
| Default export cho component (trừ page/layout) | Named export |
| `index.tsx` re-export lung tung | Import trực tiếp từ file |
| Inline 200 dòng JSX | Tách sub-component |
| Hardcode URL `/api/v1/...` trong component | Qua `lib/api.ts` |
| Tailwind class quá dài inline | Dùng `cn()` + extract custom class |