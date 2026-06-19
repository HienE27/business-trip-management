# State Management

> Quy ước quản lý state trong frontend.

---

## Nguyên tắc

1. **Server state** (data từ API) → fetch qua `api` wrapper, KHÔNG cache thủ công.
2. **Global UI state** → React Context.
3. **Local component state** → `useState` / `useReducer`.
4. **Derived state** → `useMemo`, KHÔNG duplicate vào state.

---

## Context providers

### `AuthProvider` (`components/auth/AuthProvider.tsx`)

Quản lý: user hiện tại, login/logout, JWT token.

```typescript
"use client";
import { createContext, useContext, useState, useEffect, ReactNode } from "react";
import { api } from "@/lib/api";
import type { Staff } from "@/types/api";

interface AuthContextValue {
  user: Staff | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Staff | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem("auth_token");
    if (token) {
      api.get<Staff>("/staff/me")
        .then(setUser)
        .catch(() => localStorage.removeItem("auth_token"))
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

  const login = async (username: string, password: string) => {
    const res = await api.post<AuthResponse, LoginRequest>("/auth/login", {
      username, password,
    });
    localStorage.setItem("auth_token", res.token);
    setUser(res.user);
  };

  const logout = () => {
    localStorage.removeItem("auth_token");
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
};
```

### `NotificationContext` (`components/ui/NotificationContext.tsx`)

Quản lý: notification list, mark as read.

```typescript
interface NotificationContextValue {
  notifications: Notification[];
  unreadCount: number;
  markAsRead: (id: number) => void;
  markAllAsRead: () => void;
  refresh: () => Promise<void>;
}
```

### `ToastProvider` (`components/ui/ToastProvider.tsx`)

Quản lý: toast queue.

```typescript
import { useToast } from "@/components/ui/ToastProvider";

const { showToast } = useToast();
showToast({ type: "success", message: "Tạo lịch thành công" });
showToast({ type: "error", message: "Lịch bị xung đột" });
```

---

## Local state với useState

```typescript
const [form, setForm] = useState({
  staffId: "",
  date: "",
  shiftType: "",
});
const [submitting, setSubmitting] = useState(false);
const [error, setError] = useState("");
```

**Quy tắc**:
- Đặt tên state: `<noun>` + `Set` (cho setter).
- Boolean state: prefix `is` / `has` / `should` (VD: `isOpen`, `hasConflict`).
- Nhóm related fields vào object (form state).
- Reset state khi component unmount nếu cần (thường tự xử lý).

---

## useReducer cho state phức tạp

```typescript
type FormState =
  | { step: "select-staff" }
  | { step: "select-date"; staffId: number }
  | { step: "select-shift"; staffId: number; date: string }
  | { step: "review"; form: ScheduleFormData }
  | { step: "submitting"; form: ScheduleFormData }
  | { step: "success"; schedule: Schedule }
  | { step: "error"; error: string };

function formReducer(state: FormState, action: FormAction): FormState {
  switch (action.type) {
    case "SELECT_STAFF":
      return { step: "select-date", staffId: action.staffId };
    // ...
  }
}
```

Dùng `useReducer` khi:
- State có nhiều trường liên quan chặt chẽ.
- Có nhiều transition phức tạp.
- Logic update state phức tạp hơn set value đơn giản.

---

## useEffect rules

```typescript
// ✅ GOOD: dependency array chính xác
useEffect(() => {
  api.get(`/staff/${id}`).then(setStaff);
}, [id]);

// ❌ BAD: thiếu dependency
useEffect(() => {
  api.get(`/staff/${id}`).then(setStaff);
}, []); // eslint sẽ warn

// ✅ GOOD: cleanup khi subscribe
useEffect(() => {
  const ws = new WebSocket("/ws/notifications");
  ws.onmessage = (e) => setNotifications(JSON.parse(e.data));
  return () => ws.close();
}, []);

// ❌ BAD: race condition
useEffect(() => {
  let ignore = false;
  api.get(`/staff/${id}`).then((data) => {
    if (!ignore) setStaff(data); // ← nhớ check ignore
  });
  return () => { ignore = true; };
}, [id]);
```

---

## Data fetching patterns

### Pattern 1: Server Component (khuyến nghị cho page chính)

```typescript
// app/staff/page.tsx — Server Component
import { api } from "@/lib/api";

export default async function StaffPage() {
  const staff = await api.get<Staff[]>("/staff");
  return <StaffList data={staff} />;
}
```

### Pattern 2: Client Component với initial data

```typescript
// components/staff/StaffList.tsx
"use client";
import { useState } from "react";

export function StaffList({ data: initial }: { data: Staff[] }) {
  const [data, setData] = useState(initial);
  const refresh = async () => setData(await api.get("/staff"));
  return (
    <>
      <button onClick={refresh}>Làm mới</button>
      <List items={data} />
    </>
  );
}
```

### Pattern 3: Custom hook

```typescript
// hooks/useApi.ts
"use client";
import { useState, useEffect } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";

export function useApi<T>(url: string | null) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!url) return;
    let ignore = false;
    setLoading(true);
    api.get<T>(url)
      .then((d) => { if (!ignore) setData(d); })
      .catch((err) => { if (!ignore) setError(getErrorMessage(err)); })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [url]);

  return { data, loading, error, refetch: () => url && api.get<T>(url).then(setData) };
}

// Usage
const { data, loading, error, refetch } = useApi<Staff[]>("/staff");
```

---

## Form state với controlled inputs

```typescript
const [form, setForm] = useState({ name: "", email: "" });

const update = <K extends keyof typeof form>(key: K, value: typeof form[K]) => {
  setForm((prev) => ({ ...prev, [key]: value }));
};

<input
  value={form.name}
  onChange={(e) => update("name", e.target.value)}
/>
```

Hoặc dùng helper `useForm`:

```typescript
function useForm<T extends Record<string, unknown>>(initial: T) {
  const [values, setValues] = useState(initial);
  const update = <K extends keyof T>(key: K, value: T[K]) =>
    setValues((prev) => ({ ...prev, [key]: value }));
  const reset = () => setValues(initial);
  return { values, update, reset, setValues };
}
```

---

## Khi nào KHÔNG dùng Context

- **Form state** → dùng `useState` cục bộ hoặc `useReducer`.
- **Data fetching cache** → cân nhắc React Query / SWR (chưa có trong project, P2).
- **Component tree quá sâu** → composition thay vì context.

---

## Anti-patterns

| ❌ KHÔNG | ✅ DÙNG |
|---|---|
| Global state cho mọi thứ | Local state + Context cho global thật sự |
| Context value thay đổi mỗi render | `useMemo` value hoặc tách state |
| Mutate state trực tiếp (`state.x = 1`) | Luôn `setState(prev => ({...prev, x: 1}))` |
| `useEffect` chain để sync state | Tính derived value với `useMemo` |
| Fetch trong `useEffect` không có cleanup | Cleanup với `ignore` flag hoặc AbortController |
| `useState(initial)` với object/array tạo mới mỗi render | Dùng `useState(() => initial)` cho lazy init |
| Re-render toàn tree vì context thay đổi | Tách nhiều Context nhỏ, dùng `useMemo` |
| Boolean flag cho state machine nhiều bước | `useReducer` với discriminated union |

---

## Shared data layer (Phase A pattern)

Khi nhiều page cùng dùng chung 1 nhóm data (ví dụ: periods, schedules, conflicts cho cả Dashboard và Monthly Schedule), **TÁCH data layer ra hook riêng** thay vì copy-paste vào từng page.

### Quy tắc

1. **Data layer hook** (`useSchedulePeriodData`, `useStaff`, `useAlgorithmMetrics`, ...) → fetch + state + actions. Stateless về business logic.
2. **Business logic hook** (`useScheduleWorkspace`, `useAlgorithmRun`, ...) → wrap data layer + thêm action nghiệp vụ (publish, send notification, run algo).
3. **Page** → chỉ wire UI + form local state + side effects riêng (export, modal).

### Ví dụ

```typescript
// hooks/useSchedulePeriodData.ts — data layer
export function useSchedulePeriodData(options) {
  // fetch /periods, /staff/active, /schedules/period/:id, /schedules/conflicts/check/:id
  // trả về: periods, selectedPeriodId, schedules, conflictData, loading, message,
  //         setSelectedPeriodId, refresh, setMessage, clearMessage
}

// hooks/useScheduleWorkspace.ts — business actions
export function useScheduleWorkspace() {
  const data = useSchedulePeriodData();
  return {
    ...data,
    publishPeriod,    // gọi /periods/:id/publish + setMessage
    sendNotifications,
  };
}

// app/dashboard/page.tsx — chỉ dùng data layer
const data = useSchedulePeriodData({ conflictPollMs: 60000 });

// app/monthly-schedule/page.tsx — dùng business actions
const [state, actions] = useScheduleWorkspace();
```

### Lợi ích

- Sửa logic fetch 1 chỗ.
- Bug "không load lại sau khi publish" chỉ xuất hiện ở 1 file.
- Dashboard có thể dùng `conflictPollMs: 60000` mà monthly-schedule không cần (và ngược lại).

---

## URL state (Phase B pattern)

State filter (`tab`, `staffId`, `date`, `periodId`, ...) **NÊN** lưu trên URL query thay vì `useState` local. Lý do:

- Refresh giữ filter
- Share link
- Back/forward button hoạt động
- Cùng URL shape giữa các page

### `useScheduleFilters` — shared filter hook

```typescript
// hooks/useScheduleFilters.ts
export function useScheduleFilters(options?: { basePath?: string; push?: boolean }) {
  // Đọc/ghi: ?tab=L02, ?staffId=42, ?date=2026-06-20
  return { selectedTab, selectedStaffId, selectedDate, setTab, setStaffId, setDate, applyFilters };
}
```

### Khi nào KHÔNG dùng URL state

- Modal đang mở → dùng local state (đóng modal không nên đổi URL).
- Form nhập liệu nửa chừng → dùng local state (debounce URL update).
- State private 1 component (hover, focus) → local state.

### Khi nào dùng URL

- Filter list (chip, dropdown, date range)
- Selected tab/page (overview/conflicts/summary)
- Focus item (selectedScheduleId, selectedStaffId) khi cần share.

---