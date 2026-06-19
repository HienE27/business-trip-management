# API Integration

> Quy ước gọi API backend từ frontend.

---

## Base configuration

- **Base URL**: `http://localhost:8080/api/v1` (dev), qua env var `NEXT_PUBLIC_API_URL`
- **Auth**: JWT bearer token, lưu trong `localStorage` (key: `auth_token`) hoặc httpOnly cookie
- **Content-Type**: `application/json`
- **Timezone**: client = user's local; gửi date theo ISO `YYYY-MM-DD`

---

## `lib/api.ts` — Centralized client

```typescript
// lib/api.ts
import axios, { AxiosInstance, AxiosError, AxiosRequestConfig } from "axios";

const apiClient: AxiosInstance = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api/v1",
  headers: { "Content-Type": "application/json" },
  timeout: 30_000,
});

// Request interceptor: gắn JWT token
apiClient.interceptors.request.use((config) => {
  if (typeof window !== "undefined") {
    const token = localStorage.getItem("auth_token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

// Response interceptor: unwrap ApiResponse<T> → T
apiClient.interceptors.response.use(
  (response) => response.data, // ApiResponse wrapper, data chính ở .data
  (error) => Promise.reject(error)
);

export const api = {
  get: <T>(url: string, config?: AxiosRequestConfig) =>
    apiClient.get<ApiResponse<T>>(url, config).then((r) => r.data.data),

  post: <T, B = unknown>(url: string, body?: B, config?: AxiosRequestConfig) =>
    apiClient.post<ApiResponse<T>>(url, body, config).then((r) => r.data.data),

  put: <T, B = unknown>(url: string, body?: B, config?: AxiosRequestConfig) =>
    apiClient.put<ApiResponse<T>>(url, body, config).then((r) => r.data.data),

  patch: <T, B = unknown>(url: string, body?: B, config?: AxiosRequestConfig) =>
    apiClient.patch<ApiResponse<T>>(url, body, config).then((r) => r.data.data),

  delete: <T>(url: string, config?: AxiosRequestConfig) =>
    apiClient.delete<ApiResponse<T>>(url, config).then((r) => r.data.data),
};
```

---

## Sử dụng

### Server Component

```typescript
// app/staff/page.tsx
import { api } from "@/lib/api";

export default async function StaffPage() {
  const staffList = await api.get<StaffResponse[]>("/staff");
  return <StaffList initialData={staffList} />;
}
```

### Client Component

```typescript
"use client";
import { useState, useEffect } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";

export function LeaveRequestList() {
  const [data, setData] = useState<LeaveRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    api.get<LeaveRequest[]>("/leave-requests")
      .then(setData)
      .catch((err) => setError(getErrorMessage(err)))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <Skeleton />;
  if (error) return <ErrorState message={error} />;
  return <List items={data} />;
}
```

---

## Error handling

### `lib/errors.ts`

```typescript
import { AxiosError } from "axios";

interface ApiErrorBody {
  success: false;
  message: string;
  errors?: Array<{ field: string; message: string }>;
}

export function getErrorMessage(err: unknown): string {
  if (err instanceof AxiosError) {
    const body = err.response?.data as ApiErrorBody | undefined;
    if (body?.message) return body.message;
    if (err.response?.status === 401) return "Phiên đăng nhập đã hết hạn";
    if (err.response?.status === 403) return "Bạn không có quyền thực hiện thao tác này";
    if (err.response?.status === 404) return "Không tìm thấy dữ liệu";
    if (err.response?.status === 409) return body?.message ?? "Dữ liệu bị xung đột";
    if (err.response?.status && err.response.status >= 500) return "Lỗi hệ thống, vui lòng thử lại";
    if (err.message) return err.message;
  }
  if (err instanceof Error) return err.message;
  return "Đã có lỗi xảy ra";
}

export function getFieldErrors(err: unknown): Record<string, string> {
  if (err instanceof AxiosError) {
    const body = err.response?.data as ApiErrorBody | undefined;
    if (body?.errors) {
      return body.errors.reduce(
        (acc, e) => ({ ...acc, [e.field]: e.message }),
        {} as Record<string, string>
      );
    }
  }
  return {};
}
```

### Pattern trong component

```typescript
import { getErrorMessage, getFieldErrors } from "@/lib/errors";

const handleSubmit = async () => {
  try {
    await api.post("/schedules", form);
    setSuccess("Tạo lịch thành công");
  } catch (err) {
    setError(getErrorMessage(err));    // user-friendly message
    setFieldErrors(getFieldErrors(err)); // per-field errors
  }
};
```

---

## Authentication

### Login flow

```typescript
// app/(auth)/login/LoginForm.tsx
import { api } from "@/lib/api";

const handleLogin = async (username: string, password: string) => {
  const res = await api.post<AuthResponse, LoginRequest>("/auth/login", {
    username, password,
  });
  localStorage.setItem("auth_token", res.token);
  // redirect to dashboard
};
```

### Logout

```typescript
const handleLogout = () => {
  localStorage.removeItem("auth_token");
  router.push("/login");
};
```

### Token refresh / expiry

- Khi nhận 401 → redirect về `/login`, hiện toast "Phiên đăng nhập hết hạn".
- KHÔNG tự refresh token (chưa có refresh endpoint).

---

## Optimistic updates

> Quy ước: mọi mutation có UX chờ (POST/PUT/DELETE trên list hiển thị ngay) nên dùng **3-callback contract** thay vì update-then-rollback inline. Contract này đã được implement cho `QuickAddModal` (xem `db0b168`) và nên được dùng lại cho mọi modal/button tạo-sửa-xóa tiếp theo.

### 3-callback contract (recommended)

```typescript
// Component cha (vd ScheduleByTypePage) — cung cấp 3 callback
const handleOptimisticAdd = (temp: Schedule) => {
  setSchedules((prev) => [temp, ...prev]);   // instant UI update
};
const handleCommit = (tempId: number, real: Schedule) => {
  setSchedules((prev) => prev.map((s) => (s.id === tempId ? real : s)));
};
const handleRollback = (tempId: number) => {
  setSchedules((prev) => prev.filter((s) => s.id !== tempId));
};

<QuickAddModal
  ...
  onOptimisticAdd={handleOptimisticAdd}
  onCommit={handleCommit}
  onRollback={handleRollback}
  onSuccess={handleRefresh}        // fallback cho non-optimistic callers
/>
```

```typescript
// Component con (vd QuickAddModal) — implement flow
const tempId = -Date.now();                     // negative space,
                                                // không đụng id backend
const optimistic = buildOptimisticSchedule(formData, tempId);

onOptimisticAdd?.(optimistic);                  // 1) update UI ngay
try {
  const real = await api.post<Schedule>("/schedules", formData);
  onCommit?.(tempId, real);                     // 2) replace temp
} catch (err) {
  onRollback?.(tempId);                         // 3) drop temp
  setError(getErrorMessage(err));
}
```

**Lợi ích**:
- Modal không cần biết state ngoài (loose coupling, test dễ)
- Parent quyết định render strategy (prepend, append, by date…)
- `onSuccess` legacy vẫn hoạt động khi không truyền 3 callback kia → backward-compatible

### Inline pattern (chỉ khi mutation đơn giản, 1-1 với item)

```typescript
const handleApprove = async (id: number) => {
  // 1. Optimistic update
  setExchanges((prev) =>
    prev.map((ex) => (ex.id === id ? { ...ex, status: "APPROVED" } : ex))
  );

  try {
    await api.post(`/schedule-exchanges/${id}/approve`);
  } catch (err) {
    // 2. Rollback on error
    setExchanges((prev) =>
      prev.map((ex) => (ex.id === id ? { ...ex, status: "PENDING" } : ex))
    );
    setError(getErrorMessage(err));
  }
};
```

### Quy tắc id

- Real schedule id từ backend luôn dương (auto-increment)
- Temp id dùng `-Date.now()` để:
  - Sort ngược thời gian (mới nhất lên đầu) tự nhiên
  - Không bao giờ đụng id backend
  - Sort key ổn định trong cùng 1 request

### Khi KHÔNG nên dùng optimistic

- Form có nhiều side-effect (compensation day, notification) cần confirm từ server
- Action một lần không có list (vd logout, change password) — dùng `loading` + toast
- Mutate ảnh hưởng tài nguyên shared giữa nhiều user (cần server truth)

---

## Pagination

Dùng `Pagination` component (`components/ui/Pagination.tsx`):

```typescript
const [page, setPage] = useState(0);
const [pageSize, setPageSize] = useState(20);

const { data, loading } = useApi<PaginatedResponse<Staff>>(
  `/staff?page=${page}&size=${pageSize}`
);

<Pagination
  page={page}
  pageSize={pageSize}
  total={data?.totalElements ?? 0}
  onPageChange={setPage}
  onPageSizeChange={setPageSize}
/>
```

---

## Common endpoints

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/auth/login` | Login → JWT |
| GET | `/auth/me` | Current user info |
| GET | `/staff` | List nhân sự |
| GET | `/staff/{id}` | Chi tiết |
| POST | `/staff` | Tạo mới |
| PUT | `/staff/{id}` | Cập nhật |
| DELETE | `/staff/{id}` | Xóa |
| GET | `/schedules?periodId={id}` | List lịch theo kỳ |
| POST | `/schedules` | Tạo lịch (validate conflict) |
| GET | `/schedules/conflicts/check?periodId={id}` | Check conflict toàn kỳ |
| POST | `/leave-requests` | Tạo yêu cầu nghỉ |
| POST | `/leave-requests/{id}/approve` | Duyệt (manager) |
| POST | `/schedule-exchanges` | Tạo yêu cầu đổi ca |
| POST | `/schedule-exchanges/{id}/approve` | Duyệt đổi ca |
| POST | `/auto-scheduling/run` | Chạy auto scheduling |
| GET | `/auto-scheduling/preview?periodId={id}` | Preview kết quả |

---

## Anti-patterns

| ❌ KHÔNG | ✅ DÙNG |
|---|---|
| `fetch("/api/v1/staff")` raw | `api.get<StaffResponse[]>("/staff")` |
| `axios.get(...)` trực tiếp | Qua `api` wrapper |
| Gắn `Authorization` thủ công mỗi request | Interceptor trong `apiClient` tự xử lý |
| `err.message` raw trong UI | `getErrorMessage(err)` |
| Bỏ qua HTTP status code | Map 401/403/404/409/500 sang message thân thiện |
| Gọi API trong `useEffect` mỗi render | Dependency array chính xác, hoặc dùng SWR/React Query |
| Hardcode URL `/api/v1/...` | Qua `NEXT_PUBLIC_API_URL` env var |
| Mutate state rồi không rollback khi lỗi | Optimistic + rollback pattern |